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

    // ── RSI ──────────────────────────────────────────────────────────────────

    @Test
    void rsiIsNullUntilSeededThenBounded() {
        Rsi rsi = new Rsi(14);
        for (int i = 0; i < 14; i++) {
            rsi.update(BigDecimal.valueOf(100 + i)); // 14 closes = 13 changes < period
        }
        assertThat(rsi.isReady()).isFalse();
        rsi.update(BigDecimal.valueOf(114));         // 15th close = 14th change → seeded
        assertThat(rsi.isReady()).isTrue();
        assertThat(rsi.value().doubleValue()).isBetween(0.0, 100.0);
    }

    @Test
    void rsiIs100WhenOnlyGains() {
        Rsi rsi = new Rsi(14);
        for (int i = 0; i < 20; i++) {
            rsi.update(BigDecimal.valueOf(100 + i)); // strictly rising → no losses
        }
        assertThat(rsi.value()).isEqualByComparingTo("100"); // avgLoss == 0 → RSI 100
    }

    @Test
    void rsiFallsTowardZeroOnDowntrend() {
        Rsi rsi = new Rsi(14);
        for (int i = 0; i < 20; i++) {
            rsi.update(BigDecimal.valueOf(200 - i)); // strictly falling
        }
        assertThat(rsi.value().doubleValue()).isLessThan(5.0);
    }

    // ── Bollinger Bands ──────────────────────────────────────────────────────

    @Test
    void bollingerBandsCentreAndWidthOnConstantSeries() {
        BollingerBands bb = new BollingerBands(5, BigDecimal.valueOf(2));
        for (int i = 0; i < 4; i++) bb.update(BigDecimal.valueOf(100));
        assertThat(bb.isReady()).isFalse();
        bb.update(BigDecimal.valueOf(100));            // window full
        assertThat(bb.isReady()).isTrue();
        assertThat(bb.middle()).isEqualByComparingTo("100");
        assertThat(bb.width()).isEqualByComparingTo("0"); // zero variance → zero width
    }

    @Test
    void bollingerBandsWidenWithDispersion() {
        BollingerBands bb = new BollingerBands(4, BigDecimal.valueOf(2));
        bb.update(BigDecimal.valueOf(90));
        bb.update(BigDecimal.valueOf(110));
        bb.update(BigDecimal.valueOf(90));
        bb.update(BigDecimal.valueOf(110));            // mean 100, sd 10
        assertThat(bb.middle()).isEqualByComparingTo("100");
        assertThat(bb.upper().doubleValue()).isCloseTo(120.0, within(0.01)); // 100 + 2*10
        assertThat(bb.lower().doubleValue()).isCloseTo(80.0, within(0.01));  // 100 - 2*10
    }

    // ── ADX ──────────────────────────────────────────────────────────────────

    @Test
    void adxIsHighOnAStrongUptrend() {
        Adx adx = new Adx(14);
        // ~40 strictly-rising candles → persistent +DM, no −DM → ADX climbs high.
        double base = 100;
        for (int i = 0; i < 40; i++) {
            double low = base + i * 2;
            adx.update(candle(low, low + 2, low, low + 1.5, 0));
        }
        assertThat(adx.isReady()).isTrue();
        assertThat(adx.value().doubleValue()).isGreaterThan(40.0); // trending
    }

    @Test
    void adxIsLowInAChoppyRange() {
        Adx adx = new Adx(14);
        // Oscillating high/low with no directional drift → low ADX (ranging).
        for (int i = 0; i < 60; i++) {
            double mid = (i % 2 == 0) ? 100 : 101;
            adx.update(candle(mid, mid + 1, mid - 1, mid, 0));
        }
        assertThat(adx.isReady()).isTrue();
        assertThat(adx.value().doubleValue()).isLessThan(25.0); // ranging (Mean-Reversion gate)
    }

    // ── VWAP standard-deviation bands ────────────────────────────────────────

    @Test
    void vwapBandsBracketVwap() {
        Vwap vwap = new Vwap();
        vwap.update(candle(90, 90, 90, 90, 0));   // typical 90
        vwap.update(candle(110, 110, 110, 110, 0)); // typical 110 → VWAP 100, σ 10
        assertThat(vwap.value().doubleValue()).isCloseTo(100.0, within(0.01));
        assertThat(vwap.stdDev().doubleValue()).isCloseTo(10.0, within(0.01));
        assertThat(vwap.upperBand(BigDecimal.ONE).doubleValue()).isCloseTo(110.0, within(0.01));
        assertThat(vwap.lowerBand(BigDecimal.ONE).doubleValue()).isCloseTo(90.0, within(0.01));
    }
}
