package com.sarada.trading.strategy.domain.indicators;

import com.sarada.trading.common.market.Candle;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Wilder's Average True Range, incremental. */
public class Atr {

    private final int period;

    private BigDecimal atr;
    private BigDecimal prevClose;
    private BigDecimal seedSum = BigDecimal.ZERO;
    private int count;

    public Atr(int period) {
        this.period = period;
    }

    public void update(Candle candle) {
        BigDecimal tr = trueRange(candle);
        prevClose = candle.close();
        count++;
        if (count <= period) {
            seedSum = seedSum.add(tr);
            atr = seedSum.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);
            return;
        }
        atr = atr.multiply(BigDecimal.valueOf(period - 1L))
                .add(tr)
                .divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal trueRange(Candle candle) {
        BigDecimal highLow = candle.high().subtract(candle.low());
        if (prevClose == null) {
            return highLow;
        }
        BigDecimal highClose = candle.high().subtract(prevClose).abs();
        BigDecimal lowClose = candle.low().subtract(prevClose).abs();
        return highLow.max(highClose).max(lowClose);
    }

    public BigDecimal value() {
        return atr;
    }

    public boolean isReady() {
        return count >= period;
    }

    public void reset() {
        atr = null;
        prevClose = null;
        seedSum = BigDecimal.ZERO;
        count = 0;
    }
}
