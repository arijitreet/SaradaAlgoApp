package com.sarada.trading.strategy.domain.indicators;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Wilder's Relative Strength Index, incremental.
 *
 *   change   = close − prevClose
 *   gain     = max(change, 0),  loss = max(−change, 0)
 *   avgGain/avgLoss seeded with the simple average of the first `period` changes,
 *   then Wilder-smoothed: avg = (prevAvg × (period−1) + current) / period
 *   RS  = avgGain / avgLoss
 *   RSI = 100 − 100 / (1 + RS)   (RSI = 100 when avgLoss == 0)
 */
public class Rsi {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final int period;

    private BigDecimal prevClose;
    private BigDecimal avgGain;
    private BigDecimal avgLoss;
    private BigDecimal gainSum = BigDecimal.ZERO;
    private BigDecimal lossSum = BigDecimal.ZERO;
    private BigDecimal rsi;
    private int changeCount;

    public Rsi(int period) {
        this.period = period;
    }

    public void update(BigDecimal close) {
        if (prevClose == null) {
            prevClose = close;
            return;
        }
        BigDecimal change = close.subtract(prevClose);
        prevClose = close;
        BigDecimal gain = change.signum() > 0 ? change : BigDecimal.ZERO;
        BigDecimal loss = change.signum() < 0 ? change.negate() : BigDecimal.ZERO;
        changeCount++;

        if (changeCount <= period) {
            gainSum = gainSum.add(gain);
            lossSum = lossSum.add(loss);
            if (changeCount == period) {
                avgGain = gainSum.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);
                avgLoss = lossSum.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);
                rsi = compute(avgGain, avgLoss);
            }
            return;
        }

        avgGain = avgGain.multiply(BigDecimal.valueOf(period - 1L)).add(gain)
                .divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);
        avgLoss = avgLoss.multiply(BigDecimal.valueOf(period - 1L)).add(loss)
                .divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);
        rsi = compute(avgGain, avgLoss);
    }

    private BigDecimal compute(BigDecimal gain, BigDecimal loss) {
        if (loss.signum() == 0) {
            return HUNDRED;
        }
        BigDecimal rs = gain.divide(loss, MathContext.DECIMAL64);
        return HUNDRED.subtract(HUNDRED.divide(BigDecimal.ONE.add(rs), 2, RoundingMode.HALF_UP));
    }

    /** Current RSI (0–100); null until the seeding period completes. */
    public BigDecimal value() {
        return rsi;
    }

    public boolean isReady() {
        return rsi != null;
    }

    public void reset() {
        prevClose = null;
        avgGain = null;
        avgLoss = null;
        gainSum = BigDecimal.ZERO;
        lossSum = BigDecimal.ZERO;
        rsi = null;
        changeCount = 0;
    }
}
