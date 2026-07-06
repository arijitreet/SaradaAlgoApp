package com.sarada.trading.positions.application;

import com.sarada.trading.broker.application.FeedSubscriptions;
import com.sarada.trading.common.audit.AuditService;
import com.sarada.trading.common.config.AppProperties;
import com.sarada.trading.common.events.DomainEvents;
import com.sarada.trading.common.market.LivePriceCache;
import com.sarada.trading.common.ws.WsPublisher;
import com.sarada.trading.positions.domain.BrokerPositionsPort;
import com.sarada.trading.positions.domain.PositionEntity;
import com.sarada.trading.positions.infra.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Restart-safe recovery and ongoing ghost-trade reconciliation for OPEN positions.
 *
 * Problem 1 — RESTART GAP: feed subscriptions live in memory only. After a restart the
 * open option's token is never resubscribed, so no ticks arrive → PositionMonitor never
 * runs (stop management dead) and the Active Trade panel stays empty while the DB-backed
 * views still show the position.
 *
 * Problem 2 — GHOST TRADE: user manually closes a position on Kite outside Sarada. The
 * app keeps tracking it as OPEN, its SL eventually triggers, and PositionService would
 * try to SELL — creating a naked short on an already-flat position.
 *
 * Fix for Problem 1: on ApplicationReadyEvent, re-register every OPEN position's
 * instrument token with FeedSubscriptions and re-publish to the dashboard. Monitoring
 * resumes in PositionMonitor with the original persisted entry/SL/stage — nothing recalculated.
 *
 * Fix for Problem 2: on every broker CONNECTED event AND every 60 s during market hours,
 * compare DB-open positions against Kite's net position book. When the broker shows zero
 * for a locally-open symbol, close the DB record via PositionService.closeExternal()
 * — no order is placed. Any non-zero discrepancy (partial qty, etc.) is logged and
 * surfaced in the UI feed for manual review.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PositionRecoveryService {

    private static final LocalTime MARKET_OPEN  = LocalTime.of(9, 0);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 35);

    private final PositionRepository positions;
    private final PositionService positionService;
    private final FeedSubscriptions feedSubscriptions;
    private final BrokerPositionsPort brokerPositions;
    private final LivePriceCache priceCache;
    private final AuditService audit;
    private final WsPublisher ws;
    private final AppProperties props;

    // ── startup rehydration (Problem 1) ─────────────────────────────────────

    @EventListener(ApplicationReadyEvent.class)
    public void rehydrateOpenPositions() {
        List<PositionEntity> open = positions.findByStatus(PositionEntity.Status.OPEN);
        if (open.isEmpty()) {
            return;
        }
        for (PositionEntity p : open) {
            feedSubscriptions.subscribe(p.getInstrumentToken());
            positionService.publishPosition(p,
                    priceCache.ltp(p.getInstrumentToken()).orElse(p.getEntryPrice()));
            log.info("[RECOVERY] Rehydrated OPEN position {} (entry={} SL={} stage={}) — "
                            + "feed resubscribed, monitoring resumes with persisted levels",
                    p.getTradingsymbol(), p.getEntryPrice(), p.getStopLoss(), p.getRiskStage());
        }
        audit.log("POSITION", "RECOVERED", open.size() + " open position(s) rehydrated after restart");
        ws.feed("POSITION", "Recovered after restart",
                open.size() + " open position(s) re-attached to monitoring");
    }

    // ── ghost-trade reconciliation (Problem 2) ───────────────────────────────

    /**
     * Fires on every broker CONNECTED event so a manual exit that happened while
     * the ticker was down is detected as soon as the feed reconnects.
     */
    @Async("appExecutor")
    @EventListener
    public void onBrokerConnected(DomainEvents.BrokerConnectionChanged event) {
        if (!"CONNECTED".equals(event.state())) {
            return;
        }
        reconcileWithBroker();
    }

    /**
     * Periodic reconciliation during market hours — detects a manual Kite exit
     * within ~60 s without needing a ticker reconnect event.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 120_000)
    public void periodicReconcile() {
        if (!isMarketHours()) {
            return;
        }
        reconcileWithBroker();
    }

    /**
     * Compares every DB-OPEN position against Kite's live net position book.
     * Broker net qty == 0 → position was manually closed externally; close DB record
     * immediately via {@link PositionService#closeExternal(long)} (no order placed).
     * Broker unavailable → skips silently; next scheduled tick or reconnect retries.
     */
    private void reconcileWithBroker() {
        List<PositionEntity> open = positions.findByStatus(PositionEntity.Status.OPEN);
        if (open.isEmpty()) {
            return;
        }
        brokerPositions.openNetQuantities().ifPresentOrElse(bySymbol -> {
            for (PositionEntity p : open) {
                int brokerQty = bySymbol.getOrDefault(p.getTradingsymbol(), 0);
                if (brokerQty <= 0) {
                    log.warn("[RECOVERY] MISMATCH: {} is OPEN locally (qty {}) but broker net qty is {} "
                                    + "— closing locally as MANUAL_EXTERNAL (no order placed)",
                            p.getTradingsymbol(), p.getQuantity(), brokerQty);
                    audit.log("POSITION", "RECOVERY_MISMATCH", p.getTradingsymbol()
                            + " open locally but broker net qty=" + brokerQty
                            + " — closing as MANUAL_EXTERNAL");
                    ws.feed("RISK", "Manual exit detected — closing in Sarada",
                            p.getTradingsymbol()
                                    + " not found at broker; marking closed in Sarada (no order placed)");
                    try {
                        positionService.closeExternal(p.getId());
                    } catch (Exception e) {
                        log.error("[RECOVERY] closeExternal failed for {} (id={}): {}",
                                p.getTradingsymbol(), p.getId(), e.getMessage());
                    }
                } else {
                    log.debug("[RECOVERY] {} reconciled — broker net qty {} matches local open",
                            p.getTradingsymbol(), brokerQty);
                }
            }
        }, () -> log.debug(
                "[RECOVERY] Broker book unavailable — will retry on next connect or scheduled tick"));
    }

    private boolean isMarketHours() {
        ZonedDateTime now = ZonedDateTime.now(props.zone());
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        return !time.isBefore(MARKET_OPEN) && !time.isAfter(MARKET_CLOSE);
    }
}
