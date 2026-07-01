package com.sarada.trading.positions.application;

import com.sarada.trading.common.audit.AuditService;
import com.sarada.trading.common.error.DomainException;
import com.sarada.trading.common.events.DomainEvents;
import com.sarada.trading.common.market.LivePriceCache;
import com.sarada.trading.common.time.TradingClock;
import com.sarada.trading.common.ws.WsPublisher;
import com.sarada.trading.orders.application.OrderService;
import com.sarada.trading.orders.domain.OrderEntity;
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
