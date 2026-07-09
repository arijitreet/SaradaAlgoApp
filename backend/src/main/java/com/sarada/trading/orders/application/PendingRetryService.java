package com.sarada.trading.orders.application;

import com.sarada.trading.common.audit.AuditService;
import com.sarada.trading.common.events.DomainEvents;
import com.sarada.trading.common.market.TradeSignal;
import com.sarada.trading.common.time.TradingClock;
import com.sarada.trading.orders.domain.PendingRetryEntity;
import com.sarada.trading.orders.infra.PendingRetryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Remembers signals rejected by Kite due to insufficient funds and retries them
 * once a position closes (freeing margin). Only insufficient-funds rejections are
 * stored; any other rejection type is ignored. One WAITING retry per strategy per
 * day is enforced. Stale retries from previous days are expired on startup and
 * whenever a new day's first close is seen.
 *
 * Revalidation approach: since TradingStrategy is candle-driven with no
 * evaluateNow() method, revalidation goes through the full normal entry gate
 * (session window, daily limit, concurrent-slot, strike-conflict). If the gate
 * rejects, the retry is discarded cleanly. This is the "natural cycle" approach
 * called out in the implementation rules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PendingRetryService {

    private final PendingRetryRepository retries;
    private final TradingClock clock;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    @PostConstruct
    void expireStaleRetries() {
        LocalDate today = clock.tradingDay();
        List<PendingRetryEntity> stale = retries.findByStatusAndTradingDayBefore(
                PendingRetryEntity.Status.WAITING, today);
        for (PendingRetryEntity r : stale) {
            r.setStatus(PendingRetryEntity.Status.EXPIRED);
            r.setUpdatedAt(Instant.now());
            retries.save(r);
            log.info("[RETRY] Expired stale pending retry from {} for strategy={}", r.getTradingDay(), r.getStrategyId());
        }
        if (!stale.isEmpty()) {
            audit.log("RETRY", "EXPIRED_STALE", stale.size() + " pending retries from previous days expired on startup");
        }
    }

    /**
     * Persists a pending retry for a funds-rejected signal. Called only when the
     * rejection message matches the insufficient-funds pattern.
     */
    public void createPendingRetry(TradeSignal signal, String rejectReason) {
        LocalDate today = clock.tradingDay();
        if (retries.existsByStrategyIdAndTradingDayAndStatus(
                signal.strategyId(), today, PendingRetryEntity.Status.WAITING)) {
            log.info("[RETRY] Pending retry already WAITING for strategy={} on {} — not stacking another",
                    signal.strategyId(), today);
            return;
        }
        PendingRetryEntity entity = PendingRetryEntity.from(signal, today, rejectReason);
        retries.save(entity);
        log.warn("[RETRY] Funds rejection — pending retry created for strategy={} signal={} @ {}",
                signal.strategyId(), signal.type(), signal.triggerPrice());
        audit.log("RETRY", "CREATED", signal.strategyId() + " " + signal.type()
                + " @ " + signal.triggerPrice() + " — waiting for a slot to free");
    }

    /**
     * On every position close, attempt all WAITING retries for today. The signal
     * is re-emitted via RetrySignalRequested → StrategyEngine → SignalGenerated →
     * TradeEntryOrchestrator, which runs it through the full normal entry gate.
     * "Re-verification" is delegated to the gate: if the session ended, the daily
     * limit is exhausted, or the strategy's slot is already taken, the retry is
     * discarded there without opening a position.
     */
    @EventListener
    public void onPositionClosed(DomainEvents.PositionClosed event) {
        LocalDate today = clock.tradingDay();

        // Expire any WAITING retries left over from a previous day (day-change without restart).
        expirePreviousDayRetries(today);

        List<PendingRetryEntity> waiting =
                retries.findByStatusAndTradingDay(PendingRetryEntity.Status.WAITING, today);
        for (PendingRetryEntity retry : waiting) {
            retry.setStatus(PendingRetryEntity.Status.CONSUMED);
            retry.setUpdatedAt(Instant.now());
            retries.save(retry);

            log.info("[RETRY] Position {} closed — re-emitting pending retry for strategy={} {} @ {}",
                    event.positionId(), retry.getStrategyId(), retry.getSignalType(), retry.getTriggerPrice());
            audit.log("RETRY", "ATTEMPTING", retry.getStrategyId()
                    + " — position " + event.positionId() + " closed, retrying signal through gate");

            events.publishEvent(new DomainEvents.RetrySignalRequested(retry.toSignal()));
        }
    }

    private void expirePreviousDayRetries(LocalDate today) {
        List<PendingRetryEntity> stale = retries.findByStatusAndTradingDayBefore(
                PendingRetryEntity.Status.WAITING, today);
        for (PendingRetryEntity r : stale) {
            r.setStatus(PendingRetryEntity.Status.EXPIRED);
            r.setUpdatedAt(Instant.now());
            retries.save(r);
            log.info("[RETRY] Expired previous-day pending retry from {} for strategy={}",
                    r.getTradingDay(), r.getStrategyId());
        }
    }
}
