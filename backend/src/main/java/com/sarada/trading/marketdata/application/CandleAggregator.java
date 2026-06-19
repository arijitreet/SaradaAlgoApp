package com.sarada.trading.marketdata.application;

import com.sarada.trading.common.config.AppProperties;
import com.sarada.trading.common.events.DomainEvents;
import com.sarada.trading.common.market.Candle;
import com.sarada.trading.common.market.Tick;
import com.sarada.trading.marketdata.domain.CandleEntity;
import com.sarada.trading.marketdata.infra.CandleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds N-minute candles from the tick stream for every tracked instrument.
 * A candle closes when a tick lands in the next bucket, or via the scheduled
 * flush (covers gaps where no tick arrives right after the boundary).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandleAggregator {

    private final CandleRepository candleRepository;
    private final ApplicationEventPublisher events;
    private final AppProperties props;

    private final Map<Long, Builder> building = new ConcurrentHashMap<>();
    private final Map<Long, String> trackedSymbols = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastCumulativeVolume = new ConcurrentHashMap<>();

    /** Register an instrument for candle aggregation (idempotent). */
    public void track(long instrumentToken, String symbol) {
        trackedSymbols.put(instrumentToken, symbol);
    }

    @EventListener
    public void onTick(DomainEvents.TickReceived event) {
        Tick tick = event.tick();
        String symbol = trackedSymbols.get(tick.instrumentToken());
        if (symbol == null) {
            return;
        }
        Instant bucket = bucketOf(tick.timestamp());
        Builder builder = building.get(tick.instrumentToken());

        if (builder != null && !builder.openTime.equals(bucket)) {
            closeCandle(builder);
            builder = null;
        }
        if (builder == null) {
            long volumeBase = lastCumulativeVolume.getOrDefault(tick.instrumentToken(), tick.volumeTraded());
            builder = new Builder(tick.instrumentToken(), symbol, bucket, tick.lastPrice(), volumeBase);
            building.put(tick.instrumentToken(), builder);
        }
        builder.apply(tick);
        lastCumulativeVolume.put(tick.instrumentToken(), tick.volumeTraded());
    }

    /** Close stale candles even when no tick crosses the boundary. */
    @Scheduled(fixedDelay = 5_000)
    public void flushStale() {
        Instant currentBucket = bucketOf(Instant.now());
        building.values().removeIf(builder -> {
            if (builder.openTime.isBefore(currentBucket)) {
                closeCandle(builder);
                return true;
            }
            return false;
        });
    }

    private void closeCandle(Builder builder) {
        Candle candle = builder.build(props.trading().candleMinutes());
        try {
            if (!candleRepository.existsByInstrumentTokenAndIntervalMinutesAndOpenTime(
                    candle.instrumentToken(), candle.intervalMinutes(), candle.openTime())) {
                candleRepository.save(CandleEntity.from(candle));
            }
        } catch (Exception e) {
            log.error("Candle persist failed: {}", e.getMessage());
        }
        events.publishEvent(new DomainEvents.CandleClosed(candle));
    }

    private Instant bucketOf(Instant ts) {
        long intervalSeconds = props.trading().candleMinutes() * 60L;
        long epoch = ts.getEpochSecond();
        return Instant.ofEpochSecond(epoch - (epoch % intervalSeconds));
    }

    private static final class Builder {
        final long token;
        final String symbol;
        final Instant openTime;
        final long volumeBase;
        BigDecimal open, high, low, close;
        long lastCumulativeVolume;

        Builder(long token, String symbol, Instant openTime, BigDecimal first, long volumeBase) {
            this.token = token;
            this.symbol = symbol;
            this.openTime = openTime;
            this.volumeBase = volumeBase;
            this.open = this.high = this.low = this.close = first;
            this.lastCumulativeVolume = volumeBase;
        }

        void apply(Tick tick) {
            BigDecimal price = tick.lastPrice();
            high = high.max(price);
            low = low.min(price);
            close = price;
            lastCumulativeVolume = tick.volumeTraded();
        }

        Candle build(int intervalMinutes) {
            long volume = Math.max(0, lastCumulativeVolume - volumeBase);
            return new Candle(token, symbol, intervalMinutes, openTime, open, high, low, close, volume);
        }
    }

    /** Mid-candle view for the live UI (not used for signals). */
    public Candle snapshot(long instrumentToken) {
        Builder builder = building.get(instrumentToken);
        return builder == null ? null : builder.build(props.trading().candleMinutes());
    }
}
