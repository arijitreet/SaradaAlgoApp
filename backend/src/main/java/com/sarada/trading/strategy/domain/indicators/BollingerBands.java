package com.sarada.trading.strategy.domain.indicators;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Bollinger Bands over a rolling window of closes.
 *
 *   middle = SMA(period)
 *   sd     = population standard deviation of the window
 *   upper  = middle + k × sd
 *   lower  = middle − k × sd
 *   width  = upper − lower
 *
 * Default convention: period 20, k = 2.
 */
public class BollingerBands {

    private final int period;
    private final BigDecimal k;
    private final Deque<BigDecimal> window = new ArrayDeque<>();
    private BigDecimal sum = BigDecimal.ZERO;

    private BigDecimal middle;
    private BigDecimal upper;
    private BigDecimal lower;

    public BollingerBands(int period, BigDecimal k) {
        this.period = period;
        this.k = k;
    }

    public void update(BigDecimal close) {
        window.addLast(close);
        sum = sum.add(close);
        if (window.size() > period) {
            sum = sum.subtract(window.removeFirst());
        }
        if (window.size() < period) {
            return;
        }
        middle = sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);

        BigDecimal varianceSum = BigDecimal.ZERO;
        for (BigDecimal v : window) {
            BigDecimal diff = v.subtract(middle);
            varianceSum = varianceSum.add(diff.multiply(diff));
        }
        BigDecimal variance = varianceSum.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
        BigDecimal sd = variance.sqrt(MathContext.DECIMAL64);
        BigDecimal offset = k.multiply(sd);
        upper = middle.add(offset).setScale(4, RoundingMode.HALF_UP);
        lower = middle.subtract(offset).setScale(4, RoundingMode.HALF_UP);
    }

    public BigDecimal middle() {
        return middle;
    }

    public BigDecimal upper() {
        return upper;
    }

    public BigDecimal lower() {
        return lower;
    }

    /** Band width (upper − lower); null until ready. */
    public BigDecimal width() {
        return (upper == null || lower == null) ? null : upper.subtract(lower);
    }

    public boolean isReady() {
        return middle != null;
    }

    public void reset() {
        window.clear();
        sum = BigDecimal.ZERO;
        middle = null;
        upper = null;
        lower = null;
    }
}
