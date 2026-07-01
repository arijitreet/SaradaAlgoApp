package com.sarada.trading.strategy.domain.indicators;

import com.sarada.trading.common.market.Candle;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Higher-timeframe (15-minute) trend derived from the 5-minute candle stream.
 *
 * Aggregates incoming 5-minute candles into 15-minute buckets (aligned to the
 * epoch, e.g. 09:15, 09:30, 09:45…) and tracks an EMA-fast / EMA-slow crossover
 * on the *completed* 15-minute closes. A 5-minute entry signal can then confirm
 * it is aligned with the dominant 15-minute trend.
 *
 * Reuses {@link Ema}. The 15-minute close is the close of the last 5-minute
 * candle in each bucket; a bucket is finalised when the first candle of the next
 * bucket arrives, so {@link #bullish()} reflects only fully-closed 15m candles.
 *
 * Note: the slow EMA needs ~slowPeriod × 3 five-minute candles to seed, so on a
 * from-open start it is not ready until well into the session. Callers should
 * treat a null {@link #bullish()} as "trend unknown — do not block".
 */
public class HigherTimeframeTrend {

    private static final long BUCKET_SECONDS = 15 * 60L;

    private final Ema emaFast;
    private final Ema emaSlow;

    private Instant currentBucket;
    private BigDecimal lastCloseInBucket;

    public HigherTimeframeTrend(int fastPeriod, int slowPeriod) {
        this.emaFast = new Ema(fastPeriod);
        this.emaSlow = new Ema(slowPeriod);
    }

    /** Feed one closed 5-minute candle. Finalises the previous 15m bucket on rollover. */
    public void update(Candle fiveMinCandle) {
        Instant bucket = bucketOf(fiveMinCandle.openTime());
        if (currentBucket == null) {
            currentBucket = bucket;
        } else if (!bucket.equals(currentBucket)) {
            if (lastCloseInBucket != null) {
                emaFast.update(lastCloseInBucket);
                emaSlow.update(lastCloseInBucket);
            }
            currentBucket = bucket;
        }
        lastCloseInBucket = fiveMinCandle.close();
    }

    private Instant bucketOf(Instant ts) {
        long epoch = ts.getEpochSecond();
        return Instant.ofEpochSecond(epoch - (epoch % BUCKET_SECONDS));
    }

    public boolean isReady() {
        return emaFast.isReady() && emaSlow.isReady();
    }

    /** True/false trend on completed 15m closes; null until both EMAs are seeded. */
    public Boolean bullish() {
        if (!isReady()) {
            return null;
        }
        return emaFast.value().compareTo(emaSlow.value()) > 0;
    }

    public BigDecimal emaFast() {
        return emaFast.value();
    }

    public BigDecimal emaSlow() {
        return emaSlow.value();
    }

    public void reset() {
        emaFast.reset();
        emaSlow.reset();
        currentBucket = null;
        lastCloseInBucket = null;
    }
}
