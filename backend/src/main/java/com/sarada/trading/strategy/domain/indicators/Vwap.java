package com.sarada.trading.strategy.domain.indicators;

import com.sarada.trading.common.market.Candle;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Intraday volume-weighted average price over typical prices, with optional
 * standard-deviation bands (VWAP ± k·σ).
 *
 * Index feeds report zero volume — each candle then falls back to weight 1,
 * degrading gracefully to a typical-price average (and an equal-weighted σ).
 */
public class Vwap {

    private BigDecimal cumulativePv = BigDecimal.ZERO;     // Σ w·tp
    private BigDecimal cumulativePv2 = BigDecimal.ZERO;    // Σ w·tp²  (for σ)
    private BigDecimal cumulativeVolume = BigDecimal.ZERO; // Σ w

    public void update(Candle candle) {
        BigDecimal weight = candle.volume() > 0
                ? BigDecimal.valueOf(candle.volume()) : BigDecimal.ONE;
        BigDecimal tp = candle.typicalPrice();
        cumulativePv = cumulativePv.add(tp.multiply(weight));
        cumulativePv2 = cumulativePv2.add(tp.multiply(tp).multiply(weight));
        cumulativeVolume = cumulativeVolume.add(weight);
    }

    public BigDecimal value() {
        if (cumulativeVolume.signum() == 0) {
            return null;
        }
        return cumulativePv.divide(cumulativeVolume, 4, RoundingMode.HALF_UP);
    }

    /** Volume-weighted standard deviation of typical price around VWAP; null until seeded. */
    public BigDecimal stdDev() {
        BigDecimal mean = value();
        if (mean == null) {
            return null;
        }
        BigDecimal meanSquare = cumulativePv2.divide(cumulativeVolume, 8, RoundingMode.HALF_UP);
        BigDecimal variance = meanSquare.subtract(mean.multiply(mean));
        if (variance.signum() <= 0) {
            return BigDecimal.ZERO;   // guard tiny negatives from rounding
        }
        return variance.sqrt(MathContext.DECIMAL64).setScale(4, RoundingMode.HALF_UP);
    }

    /** VWAP + k·σ; null until seeded. */
    public BigDecimal upperBand(BigDecimal k) {
        BigDecimal mean = value();
        BigDecimal sd = stdDev();
        return (mean == null || sd == null) ? null : mean.add(k.multiply(sd));
    }

    /** VWAP − k·σ; null until seeded. */
    public BigDecimal lowerBand(BigDecimal k) {
        BigDecimal mean = value();
        BigDecimal sd = stdDev();
        return (mean == null || sd == null) ? null : mean.subtract(k.multiply(sd));
    }

    public void reset() {
        cumulativePv = BigDecimal.ZERO;
        cumulativePv2 = BigDecimal.ZERO;
        cumulativeVolume = BigDecimal.ZERO;
    }
}
