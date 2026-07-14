package com.sarada.trading.strategy.application;

import com.sarada.trading.common.config.AppProperties;
import com.sarada.trading.common.market.Candle;
import com.sarada.trading.common.market.SignalType;
import com.sarada.trading.common.market.TradeSignal;
import com.sarada.trading.common.time.TradingClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Signal-generation verification: drives each strategy with synthetic 5-minute
 * candles engineered to satisfy its documented entry conditions and asserts the
 * expected signal fires (test-only — no production code touched).
 */
class StrategySignalGenerationTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDate DAY = LocalDate.of(2026, 7, 6); // a Monday

    private AppProperties props;
    private TradingClock clock;

    @BeforeEach
    void setUp() {
        props = new AppProperties(
                "Asia/Kolkata", "PAPER",
                null, null,
                new AppProperties.Trading(
                        "NIFTY 50", "NSE", "NFO", 65, 1, 50,
                        LocalTime.of(9, 20), LocalTime.of(15, 5), LocalTime.of(9, 15),
                        5, 4, 2, 0, LocalTime.of(14, 30), new BigDecimal("6500")),
                new AppProperties.Risk(
                        new BigDecimal("25"), new BigDecimal("25"), new BigDecimal("50"),
                        new BigDecimal("30"), new BigDecimal("25")),
                new AppProperties.Strategy(
                        new AppProperties.Strategy.FirstCandleBreakout(9, 15, 14, new BigDecimal("8"), 20,
                                new BigDecimal("15"), 1),
                        new AppProperties.Strategy.SupertrendFlip(10, new BigDecimal("3.0"), 0,
                                new BigDecimal("15"), new BigDecimal("15"), new BigDecimal("30"),
                                new BigDecimal("15"), new BigDecimal("15")),
                        new AppProperties.Strategy.MultiConfluenceTrend(9, 21, 10, new BigDecimal("3.0"), 9, 21, 0,
                                new BigDecimal("25")),
                        new AppProperties.Strategy.MeanReversion(20, new BigDecimal("2.0"), 14,
                                new BigDecimal("30"), new BigDecimal("70"), 14, new BigDecimal("25"),
                                new BigDecimal("1.5"), 0)));
        clock = new TradingClock(props);
    }

    /** Candle #i of the day: opens at 09:15 + i×5min IST. */
    private Candle candle(int i, double open, double high, double low, double close) {
        Instant openTime = DAY.atTime(9, 15).plusMinutes(5L * i).atZone(IST).toInstant();
        return new Candle(1L, "NIFTY 50", 5, openTime,
                BigDecimal.valueOf(open), BigDecimal.valueOf(high),
                BigDecimal.valueOf(low), BigDecimal.valueOf(close), 0);
    }

    // ── First Candle Breakout — single 09:20 evaluation window ─────────────

    @Test
    void fcbFiresBuyCeWhenHighBrokenAt0920Window() {
        var fcb = new FirstCandleBreakoutStrategy(props, clock);

        // 09:15 first candle: high=110, low=95.
        assertThat(fcb.onCandle(candle(0, 100, 110, 95, 105))).isEmpty();

        // 09:20 evaluation window: prevClose=105 ≤ 110, close=115 > 110 → BUY_CE.
        // No EMA/VWAP/ATR filter — pure price breakout.
        Optional<TradeSignal> signal = fcb.onCandle(candle(1, 109, 118, 108, 115));
        assertThat(signal).isPresent();
        assertThat(signal.get().type()).isEqualTo(SignalType.BUY_CE);
        assertThat(signal.get().strategyId()).isEqualTo(FirstCandleBreakoutStrategy.ID);
        assertThat(signal.get().reason()).contains("09:20 window");

        // All subsequent candles must be silent (hasTradedToday + evaluationDone both set).
        List<TradeSignal> fired = new ArrayList<>();
        for (int i = 2; i <= 10; i++) {
            fcb.onCandle(candle(i, 115, 130, 114, 125 + i)).ifPresent(fired::add);
        }
        assertThat(fired).as("no further signal after trade taken").isEmpty();
    }

    @Test
    void fcbFiresBuyPeWhenLowBrokenAt0920Window() {
        var fcb = new FirstCandleBreakoutStrategy(props, clock);

        // 09:15 first candle: high=110, low=95.
        assertThat(fcb.onCandle(candle(0, 100, 110, 95, 105))).isEmpty();

        // 09:20 evaluation window: prevClose=105 ≥ 95, close=90 < 95 → BUY_PE.
        Optional<TradeSignal> signal = fcb.onCandle(candle(1, 96, 97, 88, 90));
        assertThat(signal).isPresent();
        assertThat(signal.get().type()).isEqualTo(SignalType.BUY_PE);
        assertThat(signal.get().reason()).contains("09:20 window");
    }

    @Test
    void fcbEvaluationWindowConsumedEvenWithNoBreakout() {
        var fcb = new FirstCandleBreakoutStrategy(props, clock);

        // 09:15 first candle: high=110, low=95.
        assertThat(fcb.onCandle(candle(0, 100, 110, 95, 105))).isEmpty();

        // 09:20 window: close=107, inside 95–110 → no signal, but window consumed.
        assertThat(fcb.onCandle(candle(1, 105, 109, 104, 107))).isEmpty();

        // Subsequent candles silent even though they would have broken out.
        List<TradeSignal> fired = new ArrayList<>();
        for (int i = 2; i <= 10; i++) {
            fcb.onCandle(candle(i, 109, 125, 108, 120 + i)).ifPresent(fired::add);
        }
        assertThat(fired).as("no signal must fire after evaluation window is consumed").isEmpty();
    }

    @Test
    void fcbGoesQuietForDayIfNoPriceBreakoutAt0920Window() {
        var fcb = new FirstCandleBreakoutStrategy(props, clock);

        // 09:15 first candle: high=110, low=95.
        assertThat(fcb.onCandle(candle(0, 100, 110, 95, 105))).isEmpty();

        // 09:20 evaluation window: close=107, inside the 95–110 range → no breakout.
        assertThat(fcb.onCandle(candle(1, 105, 109, 104, 107))).isEmpty();

        // Even a massive post-09:20 breakout must produce no signal (window already closed).
        assertThat(fcb.onCandle(candle(2, 110, 130, 109, 125))).isEmpty();
        assertThat(fcb.onCandle(candle(3, 124, 135, 123, 132))).isEmpty();
    }

    @Test
    void fcbResetsEvaluationWindowOnDayRollover() {
        var fcb = new FirstCandleBreakoutStrategy(props, clock);

        // Day 1: first candle captured; evaluation window fires BUY_CE (close=115 > high=110).
        assertThat(fcb.onCandle(candle(0, 100, 110, 95, 105))).isEmpty();
        assertThat(fcb.onCandle(candle(1, 109, 118, 108, 115)).map(TradeSignal::type))
                .contains(SignalType.BUY_CE);
        assertThat(fcb.onCandle(candle(2, 115, 125, 114, 120))).isEmpty(); // silent

        // Simulate day rollover (StrategyEngine calls reset on new-day candle).
        fcb.reset();

        // Day 2: fresh first candle (high=108, low=96); evaluation window active again.
        assertThat(fcb.onCandle(candle(0, 100, 108, 96, 103))).isEmpty();
        // 09:20 window: close=106 within 96–108, no breakout → empty, window consumed.
        assertThat(fcb.onCandle(candle(1, 103, 107, 102, 106))).isEmpty();
        // Subsequent candle (even with breakout price) must be silent.
        assertThat(fcb.onCandle(candle(2, 107, 120, 106, 116))).isEmpty();
    }

    // ── Supertrend Flip + VWAP gate ──────────────────────────────────────────

    @Test
    void supertrendFiresBuyCeOnBullishFlipAboveVwap() {
        var configService = Mockito.mock(SupertrendConfigService.class);
        Mockito.when(configService.current()).thenReturn(
                new SupertrendConfigService.SupertrendConfig(10, new BigDecimal("3.0"), 0));
        var st = new SupertrendFlipStrategy(props, clock, configService);

        List<TradeSignal> fired = new ArrayList<>();
        // 14 tight, slightly falling candles around 100 → bearish trend, VWAP ≈ 100.
        double close = 101;
        for (int i = 0; i < 14; i++) {
            close -= 0.1;
            st.onCandle(candle(i, close + 0.2, close + 0.6, close - 0.6, close)).ifPresent(fired::add);
        }
        assertThat(fired).isEmpty();

        // Strong rally: close crosses above the ratcheted upper band AND above VWAP.
        double[] rally = {103, 106, 109, 112};
        for (int i = 0; i < rally.length; i++) {
            st.onCandle(candle(14 + i, rally[i] - 2, rally[i] + 0.5, rally[i] - 2.5, rally[i]))
                    .ifPresent(fired::add);
        }
        assertThat(fired).hasSize(1);
        assertThat(fired.get(0).type()).isEqualTo(SignalType.BUY_CE);
        assertThat(fired.get(0).reason()).contains("above VWAP");
    }

    // ── Multi-Confluence Trend (EMA 9/21 cross + Supertrend + HTF) ──────────

    @Test
    void multiConfluenceFiresBuyCeOnBullishCrossWithSupertrendConfirmation() {
        var mct = new MultiConfluenceTrendStrategy(props, clock);

        List<TradeSignal> fired = new ArrayList<>();
        // 25 declining candles: EMA9 < EMA21, Supertrend bearish.
        double close = 120;
        for (int i = 0; i < 25; i++) {
            close -= 0.6;
            mct.onCandle(candle(i, close + 0.4, close + 1.0, close - 1.0, close)).ifPresent(fired::add);
        }
        // 15 strong rising candles: Supertrend flips bullish first, then EMA9 crosses EMA21.
        for (int i = 25; i < 40; i++) {
            close += 2.2;
            mct.onCandle(candle(i, close - 1.8, close + 0.6, close - 2.2, close)).ifPresent(fired::add);
        }
        assertThat(fired).hasSize(1);
        assertThat(fired.get(0).type()).isEqualTo(SignalType.BUY_CE);
        assertThat(fired.get(0).strategyId()).isEqualTo(MultiConfluenceTrendStrategy.ID);
    }

    // ── Mean Reversion (BB + RSI + ADX ranging gate + index exits) ──────────

    @Test
    void meanReversionFiresBuyCeAtLowerBandWhenOversoldAndRanging() {
        var mr = new MeanReversionStrategy(props, clock);

        List<TradeSignal> fired = new ArrayList<>();
        // 40 tight chop candles around 100 → ADX low (ranging), RSI ~50, BB narrow.
        for (int i = 0; i < 40; i++) {
            double c = (i % 2 == 0) ? 99.95 : 100.05;
            mr.onCandle(candle(i, 100, 100.4, 99.6, c)).ifPresent(fired::add);
        }
        assertThat(fired).isEmpty();

        // Sharp 3-candle drop: RSI collapses <30, close pierces lower band, ADX still <25.
        double[] drops = {97.5, 94.5, 91.0};
        for (int i = 0; i < drops.length; i++) {
            mr.onCandle(candle(40 + i, drops[i] + 2.5, drops[i] + 3.0, drops[i] - 0.5, drops[i]))
                    .ifPresent(fired::add);
        }
        assertThat(fired).isNotEmpty();
        TradeSignal s = fired.get(0);
        assertThat(s.type()).isEqualTo(SignalType.BUY_CE);
        // Index-based exits must travel on the signal (mid-BB target above entry, BB-width stop below).
        assertThat(s.indexTarget()).isNotNull();
        assertThat(s.indexStopLoss()).isNotNull();
        assertThat(s.indexStopLoss()).isLessThan(s.triggerPrice());
        assertThat(s.indexTarget()).isGreaterThan(s.triggerPrice());
    }

    // ── Time windows ─────────────────────────────────────────────────────────

    @Test
    void timeWindowsGateStrategiesCorrectly() {
        var windows = new StrategyTimeWindows(clock);

        // Global 09:15–09:30 skip applies to every strategy.
        for (String id : List.of(FirstCandleBreakoutStrategy.ID, SupertrendFlipStrategy.ID,
                MultiConfluenceTrendStrategy.ID, MeanReversionStrategy.ID)) {
            assertThat(windows.isActiveAt(id, LocalTime.of(9, 25))).as("%s @09:25", id).isFalse();
        }
        // 10:00 — trend morning window, Mean Reversion inactive.
        assertThat(windows.isActiveAt(MultiConfluenceTrendStrategy.ID, LocalTime.of(10, 0))).isTrue();
        assertThat(windows.isActiveAt(MeanReversionStrategy.ID, LocalTime.of(10, 0))).isFalse();
        // 12:00 — midday: Mean Reversion only.
        assertThat(windows.isActiveAt(MeanReversionStrategy.ID, LocalTime.of(12, 0))).isTrue();
        assertThat(windows.isActiveAt(MultiConfluenceTrendStrategy.ID, LocalTime.of(12, 0))).isFalse();
        // 14:00 — afternoon trend window.
        assertThat(windows.isActiveAt(MultiConfluenceTrendStrategy.ID, LocalTime.of(14, 0))).isTrue();
        assertThat(windows.isActiveAt(MeanReversionStrategy.ID, LocalTime.of(14, 0))).isFalse();
        // 15:01 — after trend/VWAP cutoff; FCB/Supertrend still allowed (session gate is separate).
        assertThat(windows.isActiveAt(MultiConfluenceTrendStrategy.ID, LocalTime.of(15, 1))).isFalse();
        assertThat(windows.isActiveAt(FirstCandleBreakoutStrategy.ID, LocalTime.of(15, 1))).isTrue();
    }
}
