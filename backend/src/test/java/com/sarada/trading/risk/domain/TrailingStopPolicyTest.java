package com.sarada.trading.risk.domain;

import com.sarada.trading.common.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TrailingStopPolicyTest {

    private TrailingStopPolicy policy;
    private final BigDecimal entry = new BigDecimal("100");

    private static final String FCB = "first-candle-breakout-v1";
    private static final String ST  = "supertrend-flip-v1";

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties("Asia/Kolkata", "PAPER", null, null, null,
                new AppProperties.Risk(
                        new BigDecimal("25"),
                        new BigDecimal("25"),
                        new BigDecimal("50"),
                        new BigDecimal("30"),
                        new BigDecimal("25")),
                new AppProperties.Strategy(
                        new AppProperties.Strategy.FirstCandleBreakout(9, 15, 14, new BigDecimal("8"), 20),
                        new AppProperties.Strategy.SupertrendFlip(10, new BigDecimal("3.0"), 0,
                                new BigDecimal("15"),
                                new BigDecimal("15"),
                                new BigDecimal("30"),
                                new BigDecimal("15"),
                                new BigDecimal("15")),
                        new AppProperties.Strategy.MultiConfluenceTrend(9, 21, 10, new BigDecimal("3.0"), 9, 21, 0),
                        new AppProperties.Strategy.MeanReversion(20, new BigDecimal("2.0"), 14,
                                new BigDecimal("30"), new BigDecimal("70"), 14, new BigDecimal("25"),
                                new BigDecimal("1.5"), 0),
                        new AppProperties.Strategy.VwapStrategy(14, new BigDecimal("40"), new BigDecimal("60"),
                                new BigDecimal("1.0"), new BigDecimal("2.0"), 2, 0)));
        policy = new TrailingStopPolicy(props);
    }

    @Nested
    class FcbStrategy {

        @Test
        void initialStopIsEntryMinus25() {
            assertThat(policy.initial(entry, FCB).stopLoss()).isEqualByComparingTo("75");
            assertThat(policy.target1(entry, FCB)).isEqualByComparingTo("125");
            assertThat(policy.target2(entry, FCB)).isEqualByComparingTo("150");
        }

        @Test
        void target1MovesStopToBreakeven() {
            var state = policy.advance(entry, new BigDecimal("125"),
                    new BigDecimal("75"), TrailingStopPolicy.Stage.INITIAL, FCB);
            assertThat(state.stopLoss()).isEqualByComparingTo("100");
            assertThat(state.stage()).isEqualTo(TrailingStopPolicy.Stage.BREAKEVEN);
        }

        @Test
        void target2LocksEntryPlus30() {
            var state = policy.advance(entry, new BigDecimal("150"),
                    new BigDecimal("100"), TrailingStopPolicy.Stage.BREAKEVEN, FCB);
            assertThat(state.stopLoss()).isEqualByComparingTo("130");
            assertThat(state.stage()).isEqualTo(TrailingStopPolicy.Stage.LOCKED);
        }

        @Test
        void trailsBy25ForEveryAdditional25() {
            var at75 = policy.advance(entry, new BigDecimal("175"),
                    new BigDecimal("130"), TrailingStopPolicy.Stage.LOCKED, FCB);
            assertThat(at75.stopLoss()).isEqualByComparingTo("155");
            assertThat(at75.stage()).isEqualTo(TrailingStopPolicy.Stage.TRAILING);

            var at100 = policy.advance(entry, new BigDecimal("200"),
                    at75.stopLoss(), at75.stage(), FCB);
            assertThat(at100.stopLoss()).isEqualByComparingTo("180");
        }

        @Test
        void stopNeverMovesDown() {
            var state = policy.advance(entry, new BigDecimal("110"),
                    new BigDecimal("130"), TrailingStopPolicy.Stage.LOCKED, FCB);
            assertThat(state.stopLoss()).isEqualByComparingTo("130");
            assertThat(state.stage()).isEqualTo(TrailingStopPolicy.Stage.LOCKED);
        }
    }

    @Nested
    class SupertrendStrategy {

        @Test
        void initialStopIsEntryMinus15() {
            assertThat(policy.initial(entry, ST).stopLoss()).isEqualByComparingTo("85");
            assertThat(policy.target1(entry, ST)).isEqualByComparingTo("115");
            assertThat(policy.target2(entry, ST)).isEqualByComparingTo("130");
        }

        @Test
        void target1MovesStopToBreakeven() {
            var state = policy.advance(entry, new BigDecimal("115"),
                    new BigDecimal("85"), TrailingStopPolicy.Stage.INITIAL, ST);
            assertThat(state.stopLoss()).isEqualByComparingTo("100");
            assertThat(state.stage()).isEqualTo(TrailingStopPolicy.Stage.BREAKEVEN);
        }

        @Test
        void target2LocksEntryPlus15() {
            var state = policy.advance(entry, new BigDecimal("130"),
                    new BigDecimal("100"), TrailingStopPolicy.Stage.BREAKEVEN, ST);
            assertThat(state.stopLoss()).isEqualByComparingTo("115");
            assertThat(state.stage()).isEqualTo(TrailingStopPolicy.Stage.LOCKED);
        }

        @Test
        void trailsBy15ForEveryAdditional15() {
            // +45 gain → SL = entry + 15 + 15 = 130
            var at45 = policy.advance(entry, new BigDecimal("145"),
                    new BigDecimal("115"), TrailingStopPolicy.Stage.LOCKED, ST);
            assertThat(at45.stopLoss()).isEqualByComparingTo("130");
            assertThat(at45.stage()).isEqualTo(TrailingStopPolicy.Stage.TRAILING);

            // +60 gain → SL = entry + 15 + 30 = 145
            var at60 = policy.advance(entry, new BigDecimal("160"),
                    at45.stopLoss(), at45.stage(), ST);
            assertThat(at60.stopLoss()).isEqualByComparingTo("145");
        }
    }

    @Test
    void stopHitDetection() {
        assertThat(policy.isStopHit(new BigDecimal("74.95"), new BigDecimal("75"))).isTrue();
        assertThat(policy.isStopHit(new BigDecimal("75"), new BigDecimal("75"))).isTrue();
        assertThat(policy.isStopHit(new BigDecimal("75.05"), new BigDecimal("75"))).isFalse();
    }
}
