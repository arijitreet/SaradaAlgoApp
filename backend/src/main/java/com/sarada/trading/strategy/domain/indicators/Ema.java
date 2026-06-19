package com.sarada.trading.strategy.domain.indicators;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Incremental exponential moving average, seeded with an SMA of the first N closes. */
public class Ema {

    private final int period;
    private final BigDecimal multiplier;

    private BigDecimal value;
    private BigDecimal seedSum = BigDecimal.ZERO;
    private int count;

    public Ema(int period) {
        this.period = period;
        this.multiplier = BigDecimal.valueOf(2.0 / (period + 1));
    }

    public void update(BigDecimal close) {
        count++;
        if (count <= period) {
            seedSum = seedSum.add(close);
            value = seedSum.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);
            return;
        }
        value = close.subtract(value).multiply(multiplier, MathContext.DECIMAL64)
                .add(value)
                .setScale(4, RoundingMode.HALF_UP);
    }

    /** Progressive value (SMA until seeded, then true EMA); null before first sample. */
    public BigDecimal value() {
        return value;
    }

    public boolean isReady() {
        return count >= period;
    }

    public void reset() {
        value = null;
        seedSum = BigDecimal.ZERO;
        count = 0;
    }
}
