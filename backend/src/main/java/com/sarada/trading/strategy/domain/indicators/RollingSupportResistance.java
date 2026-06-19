package com.sarada.trading.strategy.domain.indicators;

import com.sarada.trading.common.market.Candle;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;

/** Rolling intraday support/resistance: extremes of the last N closed candles. */
public class RollingSupportResistance {

    private final int lookback;
    private final Deque<Candle> window = new ArrayDeque<>();

    public RollingSupportResistance(int lookback) {
        this.lookback = lookback;
    }

    public void update(Candle candle) {
        window.addLast(candle);
        while (window.size() > lookback) {
            window.removeFirst();
        }
    }

    public BigDecimal support() {
        return window.stream().map(Candle::low).min(BigDecimal::compareTo).orElse(null);
    }

    public BigDecimal resistance() {
        return window.stream().map(Candle::high).max(BigDecimal::compareTo).orElse(null);
    }

    public void reset() {
        window.clear();
    }
}
