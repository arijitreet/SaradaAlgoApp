package com.sarada.trading.strategy.domain.indicators;

import com.sarada.trading.common.market.Candle;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Intraday volume-weighted average price over typical prices.
 * Index feeds report zero volume — each candle then falls back to weight 1,
 * degrading gracefully to a typical-price average.
 */
public class Vwap {

    private BigDecimal cumulativePv = BigDecimal.ZERO;
    private BigDecimal cumulativeVolume = BigDecimal.ZERO;

    public void update(Candle candle) {
        BigDecimal weight = candle.volume() > 0
                ? BigDecimal.valueOf(candle.volume()) : BigDecimal.ONE;
        cumulativePv = cumulativePv.add(candle.typicalPrice().multiply(weight));
        cumulativeVolume = cumulativeVolume.add(weight);
    }

    public BigDecimal value() {
        if (cumulativeVolume.signum() == 0) {
            return null;
        }
        return cumulativePv.divide(cumulativeVolume, 4, RoundingMode.HALF_UP);
    }

    public void reset() {
        cumulativePv = BigDecimal.ZERO;
        cumulativeVolume = BigDecimal.ZERO;
    }
}
