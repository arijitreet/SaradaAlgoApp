package com.sarada.trading.positions.application;

import com.sarada.trading.common.audit.AuditService;
import com.sarada.trading.common.error.DomainException;
import com.sarada.trading.common.events.DomainEvents;
import com.sarada.trading.common.market.LivePriceCache;
import com.sarada.trading.common.time.TradingClock;
import com.sarada.trading.common.ws.WsPublisher;
import com.sarada.trading.orders.application.OrderService;
import com.sarada.trading.orders.domain.OrderEntity;
import com.sarada.trading.positions.domain.BrokerPositionsPort;
import com.sarada.trading.positions.domain.PositionEntity;
import com.sarada.trading.positions.infra.PositionRepository;
import com.sarada.trading.risk.domain.TradeStatsPort;
import com.sarada.trading.risk.domain.TrailingStopPolicy;
import com.sarada.trading.strategy.domain.StrategyPerformancePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the position lifecycle: opens on EntryFilled, exits on stop/manual/
 * force-exit, and feeds the live P&L stream. Also implements the risk
 * module's TradeStatsPort (trade counting / open-position checks).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PositionService implements TradeStatsPort, StrategyPerformancePort {

    private final PositionRepository positions;
    private final OrderService orderService;
    private final BrokerPositionsPort brokerPositions;
    private final TrailingStopPolicy stopPolicy;
    private final LivePriceCache priceCache;
    private final TradingClock clock;
    private final AuditService audit;
    private final WsPublisher ws;
    private final ApplicationEventPublisher events;

    /** Positions with an exit in flight — prevents double-selling on tick races. */
    private final Set<Long> exiting = ConcurrentHashMap.newKeySet();

    // ── opening ─────────────────────────────────────────────────────────────

    @EventListener
    public void onEntryFilled(DomainEvents.EntryFilled event) {
        String sid = event.signal().strategyId();
        // Premium-ladder values are always populated (NOT NULL columns); for index-exit
        // positions they are inert placeholders — PositionMonitor branches on the index levels.
        TrailingStopPolicy.StopState initial = stopPolicy.initial(event.fillPrice(), sid);
        PositionEntity position = PositionEntity.open(
                clock.tradingDay(), sid, null, event.orderId(),
                event.instrumentToken(), event.tradingsymbol(),
                event.signal().type().optionType(), event.strike(), event.expiry(),
                event.quantity(), event.fillPrice(),
                initial.stopLoss(),
                stopPolicy.target1(event.fillPrice(), sid),
                stopPolicy.target2(event.fillPrice(), sid),
                event.signal().indexStopLoss(),
                event.signal().indexTarget());
        position = positions.save(position);

        log.info("Position OPEN {} x{} @ {} (SL {})", position.getTradingsymbol(),
                position.getQuantity(), position.getEntryPrice(), position.getStopLoss());
        audit.log("POSITION", "OPENED", position.getTradingsymbol() + " x" + position.getQuantity()
                + " @ " + position.getEntryPrice() + " SL " + position.getStopLoss());
        ws.feed("POSITION", "Position opened",
                position.getTradingsymbol() + " @ " + position.getEntryPrice());
        events.publishEvent(new DomainEvents.PositionOpened(position.getId()));
        publishPosition(position, position.getEntryPrice());
    }

    // ── exits ───────────────────────────────────────────────────────────────

    public PositionEntity exit(long positionId, String reason, String actor) {
        PositionEntity position = positions.findById(positionId)
                .orElseThrow(() -> DomainException.notFound("Position " + positionId + " not found"));
        if (position.getStatus() != PositionEntity.Status.OPEN) {
            throw DomainException.conflict("Position already closed");
        }
        if (!exiting.add(positionId)) {
            throw DomainException.conflict("Exit already in progress");
        }
        try {
            // Guard: if the broker already shows zero net qty, the position was manually
            // closed on Kite outside Sarada. Close the DB record without placing a SELL
            // order — placing one would create a naked short on an already-flat position.
            if (brokerShowsPositionGone(position)) {
                return doCloseExternal(position);
            }

            OrderEntity exitOrder = orderService.placeMarket(
                    position.getInstrumentToken(), position.getTradingsymbol(),
                    "NFO", OrderEntity.Side.SELL, position.getQuantity());

            if (exitOrder.getStatus() != OrderEntity.Status.FILLED) {
                throw new DomainException("Exit order rejected: " + exitOrder.getStatusMessage());
            }

            position.close(exitOrder.getId(), exitOrder.getAvgFillPrice(), reason);
            position = positions.save(position);

            log.info("Position CLOSED {} @ {} ({}) pnl={}", position.getTradingsymbol(),
                    position.getExitPrice(), reason, position.getRealizedPnl());
            audit.log("POSITION", "CLOSED", position.getTradingsymbol() + " @ "
                    + position.getExitPrice() + " (" + reason + ") P&L " + position.getRealizedPnl(), actor);
            ws.feed("POSITION", "Position closed " + (position.getRealizedPnl().signum() >= 0 ? "▲" : "▼"),
                    position.getTradingsymbol() + " P&L " + position.getRealizedPnl());
            events.publishEvent(new DomainEvents.PositionClosed(
                    position.getId(), position.getRealizedPnl(), reason));
            publishPosition(position, position.getExitPrice());
            publishPnl();
            return position;
        } finally {
            exiting.remove(positionId);
        }
    }

    @Async("appExecutor")
    @EventListener
    public void onForceExit(DomainEvents.ForceExitRequested event) {
        for (PositionEntity open : positions.findByStatus(PositionEntity.Status.OPEN)) {
            try {
                exit(open.getId(), "FORCE_EXIT", "SYSTEM");
            } catch (Exception e) {
                log.error("Force exit failed for position {}: {}", open.getId(), e.getMessage());
            }
        }
    }

    // ── external close (reconciliation path, no order placed) ───────────────

    /**
     * Closes a DB-open position without placing a broker order. Called by
     * {@link com.sarada.trading.positions.application.PositionRecoveryService} when
     * periodic reconciliation confirms the broker already shows zero net qty —
     * i.e. the user manually exited the position on Kite outside Sarada.
     * Exit price is the option's last known LTP (approximate; real exit price is
     * whatever the user received on Kite).
     */
    public PositionEntity closeExternal(long positionId) {
        PositionEntity position = positions.findById(positionId)
                .orElseThrow(() -> DomainException.notFound("Position " + positionId + " not found"));
        if (position.getStatus() != PositionEntity.Status.OPEN) {
            throw DomainException.conflict("Position already closed");
        }
        if (!exiting.add(positionId)) {
            throw DomainException.conflict("Exit already in progress");
        }
        try {
            return doCloseExternal(position);
        } finally {
            exiting.remove(positionId);
        }
    }

    /** Performs the actual close-without-order. Caller must hold the {@link #exiting} lock. */
    private PositionEntity doCloseExternal(PositionEntity position) {
        BigDecimal ltp = priceCache.ltp(position.getInstrumentToken()).orElse(position.getEntryPrice());
        position.close(null, ltp, "MANUAL_EXTERNAL");
        position = positions.save(position);
        log.warn("[EXIT] {} closed as MANUAL_EXTERNAL — broker net qty 0, no order placed. "
                        + "P&L≈{} (LTP at detection; real exit price is whatever user received on Kite)",
                position.getTradingsymbol(), position.getRealizedPnl());
        audit.log("POSITION", "MANUAL_EXTERNAL", position.getTradingsymbol()
                + " — broker shows net qty 0; likely manually exited on Kite. "
                + "P&L approximate (LTP at detection).");
        ws.feed("RISK", "Manual exit detected on Kite",
                position.getTradingsymbol() + " was closed outside Sarada — marked closed, no order placed");
        events.publishEvent(new DomainEvents.PositionClosed(
                position.getId(), position.getRealizedPnl(), "MANUAL_EXTERNAL"));
        publishPosition(position, position.getExitPrice());
        publishPnl();
        return position;
    }

    /** Returns true when the broker confirms zero net qty for this position's symbol. */
    private boolean brokerShowsPositionGone(PositionEntity position) {
        return brokerPositions.openNetQuantities()
                .map(book -> book.getOrDefault(position.getTradingsymbol(), 0) <= 0)
                .orElse(false); // broker unreachable → assume position exists, proceed with order
    }

    // ── TradeStatsPort (risk) ───────────────────────────────────────────────

    @Override
    public int tradesOpenedOn(LocalDate tradingDay) {
        return positions.countByTradingDay(tradingDay);
    }

    @Override
    public boolean hasOpenPosition() {
        return positions.findFirstByStatusOrderByOpenedAtDesc(PositionEntity.Status.OPEN).isPresent();
    }

    // ── StrategyPerformancePort (strategy comparison view) ──────────────────

    @Override
    public StrategyPnl pnlFor(String strategyId, LocalDate day) {
        BigDecimal realized = positions.realizedPnlByStrategyOn(day, strategyId);
        int trades = positions.countByTradingDayAndStrategyId(day, strategyId);

        OpenPositionBrief openBrief = null;
        BigDecimal unrealized = BigDecimal.ZERO;
        PositionEntity open = positions
                .findFirstByStrategyIdAndStatusOrderByOpenedAtDesc(strategyId, PositionEntity.Status.OPEN)
                .orElse(null);
        if (open != null) {
            BigDecimal ltp = priceCache.ltp(open.getInstrumentToken()).orElse(open.getEntryPrice());
            unrealized = open.unrealizedPnl(ltp);
            openBrief = new OpenPositionBrief(open.getTradingsymbol(), open.getOptionType(),
                    open.getStrike(), unrealized);
        }
        return new StrategyPnl(realized, unrealized, trades, openBrief);
    }

    // ── live streams ────────────────────────────────────────────────────────

    public record PositionView(long id, String tradingsymbol, String optionType, BigDecimal strike,
                               LocalDate expiry, int quantity, BigDecimal entryPrice,
                               BigDecimal lastPrice, BigDecimal stopLoss, BigDecimal target1,
                               BigDecimal target2, String riskStage, BigDecimal unrealizedPnl,
                               String status, Instant openedAt) {}

    public PositionView toView(PositionEntity position, BigDecimal lastPrice) {
        BigDecimal reference = lastPrice != null ? lastPrice
                : (position.getExitPrice() != null ? position.getExitPrice() : position.getEntryPrice());
        return new PositionView(position.getId(), position.getTradingsymbol(), position.getOptionType(),
                position.getStrike(), position.getExpiry(), position.getQuantity(),
                position.getEntryPrice(), reference, position.getStopLoss(), position.getTarget1(),
                position.getTarget2(), position.getRiskStage().name(),
                position.getStatus() == PositionEntity.Status.OPEN
                        ? position.unrealizedPnl(reference) : position.getRealizedPnl(),
                position.getStatus().name(), position.getOpenedAt());
    }

    public void publishPosition(PositionEntity position, BigDecimal lastPrice) {
        ws.publish(WsPublisher.TOPIC_POSITION, toView(position, lastPrice));
    }

    public record PnlSummary(LocalDate day, BigDecimal realized, BigDecimal unrealized,
                             BigDecimal total, int trades, int tradesRemainingHint) {}

    public void publishPnl() {
        LocalDate day = clock.tradingDay();
        BigDecimal realized = positions.realizedPnlOn(day);
        BigDecimal unrealized = positions.findByStatus(PositionEntity.Status.OPEN).stream()
                .map(p -> priceCache.ltp(p.getInstrumentToken())
                        .map(p::unrealizedPnl)
                        .orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        ws.publish(WsPublisher.TOPIC_PNL, new PnlSummary(day, realized, unrealized,
                realized.add(unrealized), positions.countByTradingDay(day), 0));
    }
}
