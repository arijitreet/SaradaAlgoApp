package com.sarada.trading.strategy.application;

import com.sarada.trading.common.events.DomainEvents;
import com.sarada.trading.common.market.Candle;
import com.sarada.trading.common.time.TradingClock;
import com.sarada.trading.common.ws.WsPublisher;
import com.sarada.trading.strategy.domain.SignalEntity;
import com.sarada.trading.strategy.domain.TradingStrategy;
import com.sarada.trading.strategy.infra.SignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Fans closed candles out to every registered strategy. Indicators stay warm
 * regardless of session state; signals are only emitted while the session is
 * running and inside the trading window. Multi-strategy by construction:
 * every TradingStrategy bean is wired in automatically.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyEngine {

    private final List<TradingStrategy> strategies;
    private final TradingSessionService session;
    private final TradingClock clock;
    private final SignalRepository signals;
    private final ApplicationEventPublisher events;
    private final WsPublisher ws;
    private final StrategyTimeWindows timeWindows;

    private volatile LocalDate currentDay;

    @EventListener
    public void onCandleClosed(DomainEvents.CandleClosed event) {
        processCandle(event.candle(), true);
    }

    /**
     * Past candles replayed after a late feed connect (see HistoricalCandleBackfillService).
     * Indicators and the first-candle reference are warmed up exactly as on a normal day,
     * but any signal a stale candle would have fired is suppressed — only live candles
     * (above) can open trades.
     */
    @EventListener
    public void onHistoricalCandleClosed(DomainEvents.HistoricalCandleClosed event) {
        processCandle(event.candle(), false);
    }

    private void processCandle(Candle candle, boolean allowSignals) {
        rolloverDayIfNeeded(candle);

        for (TradingStrategy strategy : strategies) {
            if (!strategy.underlying().equals(candle.symbol())) {
                continue;
            }
            Optional<com.sarada.trading.common.market.TradeSignal> signal = strategy.onCandle(candle);
            if (allowSignals && signal.isPresent()) {
                handleSignal(strategy, signal.get());
            } else {
                if (signal.isPresent()) {
                    log.debug("Replay signal suppressed: {} @ {} for candle {}",
                            signal.get().type(), signal.get().triggerPrice(), candle.openTime());
                }
                publishHealth(strategy);
            }
        }
    }

    private void handleSignal(TradingStrategy strategy, com.sarada.trading.common.market.TradeSignal signal) {
        publishHealth(strategy);
        if (!session.isRunning() || !clock.isWithinSession()) {
            log.info("Signal {} suppressed (session inactive)", signal.type());
            return;
        }
        if (!timeWindows.isActive(strategy.id())) {
            log.info("Signal {} suppressed — outside {} active window ({})",
                    signal.type(), strategy.id(), timeWindows.windowLabel(strategy.id()));
            return;
        }
        SignalEntity entity = signals.save(SignalEntity.from(signal, clock.tradingDay()));
        log.info("Signal generated: {} @ {} — {}", signal.type(), signal.triggerPrice(), signal.reason());
        ws.publish(WsPublisher.TOPIC_SIGNALS, new SignalView(entity.getId(), signal.type().name(),
                signal.triggerPrice(), signal.reason(), signal.at()));
        ws.feed("SIGNAL", signal.type().name() + " signal", signal.reason());
        events.publishEvent(new DomainEvents.SignalGenerated(entity.getId(), signal));
    }

    private void publishHealth(TradingStrategy strategy) {
        ws.publish(WsPublisher.TOPIC_INDICATORS, new IndicatorsView(
                strategy.id(), strategy.snapshot(), strategy.health(), Instant.now()));
    }

    /**
     * Re-emits a funds-rejected signal through the full normal entry gate. Applies
     * the same session/window guards as a live signal; if either fails, the retry
     * is silently discarded (gate would have blocked it anyway).
     */
    @EventListener
    public void onRetrySignalRequested(DomainEvents.RetrySignalRequested event) {
        com.sarada.trading.common.market.TradeSignal signal = event.signal();
        if (!session.isRunning() || !clock.isWithinSession()) {
            log.info("[RETRY] {} suppressed — session not active at retry time", signal.strategyId());
            return;
        }
        if (!timeWindows.isActive(signal.strategyId())) {
            log.info("[RETRY] {} suppressed — outside time window at retry time", signal.strategyId());
            return;
        }
        SignalEntity entity = signals.save(SignalEntity.from(signal, clock.tradingDay()));
        log.info("[RETRY] Re-emitting signalId={} for {} {} @ {} (funds-rejection retry)",
                entity.getId(), signal.strategyId(), signal.type(), signal.triggerPrice());
        events.publishEvent(new DomainEvents.SignalGenerated(entity.getId(), signal));
    }

    private void rolloverDayIfNeeded(Candle candle) {
        LocalDate day = clock.dayOf(candle.openTime());
        if (!day.equals(currentDay)) {
            currentDay = day;
            strategies.forEach(TradingStrategy::reset);
            log.info("Trading day rollover → {}; strategies reset", day);
        }
    }

    public record SignalView(long id, String type, java.math.BigDecimal triggerPrice,
                             String reason, Instant at) {}

    public record IndicatorsView(String strategyId,
                                 com.sarada.trading.common.market.IndicatorSnapshot indicators,
                                 TradingStrategy.Health health, Instant at) {}

    public List<TradingStrategy> activeStrategies() {
        return strategies;
    }
}
