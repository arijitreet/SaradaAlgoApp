package com.sarada.trading.risk.domain;

import com.sarada.trading.common.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TrailingStopPolicyTest {

    private TrailingStopPolicy policy;
    private final BigDecimal entry = new BigDecimal("100");

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties("Asia/Kolkata", "PAPER", null, null, null,
                new AppProperties.Risk(
                        new BigDecimal("25"),   // SL points
                        new BigDecimal("25"),   // T1
                        new BigDecimal("50"),   // T2
                        new BigDecimal("30"),   // SL offset after T2
                        new BigDecimal("25")),  // trail step
                null);
        policy = new TrailingStopPolicy(props);
    }

    @Test
    void initialStopIsEntryMinus25() {
        assertThat(policy.initial(entry).stopLoss()).isEqualByComparingTo("75");
        assertThat(policy.target1(entry)).isEqualByComparingTo("125");
        assertThat(policy.target2(entry)).isEqualByComparingTo("150");
    }

    @Test
    void target1MovesStopToBreakeven() {
        var state = policy.advance(entry, new BigDecimal("125"),
                new BigDecimal("75"), TrailingStopPolicy.Stage.INITIAL);
        assertThat(state.stopLoss()).isEqualByComparingTo("100");
        assertThat(state.stage()).isEqualTo(TrailingStopPolicy.Stage.BREAKEVEN);
    }

    @Test
    void target2LocksEntryPlus30() {
        var state = policy.advance(entry, new BigDecimal("150"),
                new BigDecimal("100"), TrailingStopPolicy.Stage.BREAKEVEN);
        assertThat(state.stopLoss()).isEqualByComparingTo("130");
        assertThat(state.stage()).isEqualTo(TrailingStopPolicy.Stage.LOCKED);
    }

    @Test
    void trailsBy25ForEveryAdditional25() {
        // +75 gain → SL = entry + 30 + 25 = 155
        var at75 = policy.advance(entry, new BigDecimal("175"),
                new BigDecimal("130"), TrailingStopPolicy.Stage.LOCKED);
        assertThat(at75.stopLoss()).isEqualByComparingTo("155");
        assertThat(at75.stage()).isEqualTo(TrailingStopPolicy.Stage.TRAILING);

        // +100 gain → SL = entry + 30 + 50 = 180
        var at100 = policy.advance(entry, new BigDecimal("200"),
                at75.stopLoss(), at75.stage());
        assertThat(at100.stopLoss()).isEqualByComparingTo("180");
    }

    @Test
    void stopNeverMovesDown() {
        var state = policy.advance(entry, new BigDecimal("110"),
                new BigDecimal("130"), TrailingStopPolicy.Stage.LOCKED);
        assertThat(state.stopLoss()).isEqualByComparingTo("130");
        assertThat(state.stage()).isEqualTo(TrailingStopPolicy.Stage.LOCKED);
    }

    @Test
    void stopHitDetection() {
        assertThat(policy.isStopHit(new BigDecimal("74.95"), new BigDecimal("75"))).isTrue();
        assertThat(policy.isStopHit(new BigDecimal("75"), new BigDecimal("75"))).isTrue();
        assertThat(policy.isStopHit(new BigDecimal("75.05"), new BigDecimal("75"))).isFalse();
    }
}
