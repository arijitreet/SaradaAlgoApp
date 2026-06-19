package com.sarada.trading.strategy.domain.indicators;

import com.sarada.trading.common.market.Candle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class IndicatorsTest {

    private Candle candle(double open, double high, double low, double close, long volume) {
        return new Candle(1L, "TEST", 5, Instant.now(),
                BigDecimal.valueOf(open), BigDecimal.valueOf(high),
                BigDecimal.valueOf(low), BigDecimal.valueOf(close), volume);
    }

    @Test
    void emaSeedsWithSmaThenSmooths() {
        Ema ema = new Ema(3);
        ema.update(BigDecimal.valueOf(10));
        ema.update(BigDecimal.valueOf(20));
        ema.update(BigDecimal.valueOf(30));
        assertThat(ema.isReady()).isTrue();
        assertThat(ema.value()).isEqualByComparingTo("20.0000"); // SMA seed

        ema.update(BigDecimal.valueOf(40));
        // EMA = 40*0.5 + 20*0.5 = 30
        assertThat(ema.value().doubleValue()).isCloseTo(30.0, within(0.01));
    }

    @Test
    void vwapWeightsByVolume() {
        Vwap vwap = new Vwap();
        vwap.update(candle(100, 110, 90, 100, 100));  // typical 100, vol 100
        vwap.update(candle(100, 130, 110, 120, 300)); // typical 120, vol 300
        // (100*100 + 120*300) / 400 = 115
        assertThat(vwap.value().doubleValue()).isCloseTo(115.0, within(0.01));
    }

    @Test
    void vwapFallsBackToTypicalPriceAverageWithoutVolume() {
        Vwap vwap = new Vwap();
        vwap.update(candle(100, 110, 90, 100, 0));  // typical 100
        vwap.update(candle(100, 130, 110, 120, 0)); // typical 120
        assertThat(vwap.value().doubleValue()).isCloseTo(110.0, within(0.01));
    }

    @Test
    void atrUsesTrueRangeIncludingGaps() {
        Atr atr = new Atr(2);
        atr.update(candle(100, 110, 90, 105, 0));   // TR = 20 (no prev close)
        atr.update(candle(130, 140, 125, 135, 0));  // TR = max(15, |140-105|, |125-105|) = 35
        // seed SMA = (20+35)/2 = 27.5
        assertThat(atr.isReady()).isTrue();
        assertThat(atr.value().doubleValue()).isCloseTo(27.5, within(0.01));
    }

    @Test
    void supportResistanceTracksWindowExtremes() {
        RollingSupportResistance sr = new RollingSupportResistance(2);
        sr.update(candle(100, 120, 80, 110, 0));
        sr.update(candle(110, 140, 100, 130, 0));
        sr.update(candle(130, 135, 105, 120, 0)); // first candle drops out of window
        assertThat(sr.resistance()).isEqualByComparingTo("140");
        assertThat(sr.support()).isEqualByComparingTo("100");
    }
}
