package com.sarada.trading.positions.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the index-based exit decision (Mean Reversion / VWAP strategies):
 * a CE exits when the index rises to target or falls to stop; a PE is the mirror.
 */
class IndexExitTest {

    private PositionEntity position(String optionType, String indexStop, String indexTarget) {
        return PositionEntity.open(
                LocalDate.now(), "mean-reversion-v1", null, 1L, 111L, "NIFTY-OPT",
                optionType, new BigDecimal("20000"), LocalDate.now(), 50,
                new BigDecimal("150"),                 // entry premium
                new BigDecimal("0.05"), new BigDecimal("999"), new BigDecimal("999"),  // inert premium fields
                indexStop == null ? null : new BigDecimal(indexStop),
                indexTarget == null ? null : new BigDecimal(indexTarget));
    }

    @Test
    void ceExitsAtTargetAboveAndStopBelow() {
        // CE: target 20050 (above entry index), stop 19970 (below).
        PositionEntity ce = position("CE", "19970", "20050");
        assertThat(ce.indexExitReason(new BigDecimal("20055"))).isEqualTo("TARGET");
        assertThat(ce.indexExitReason(new BigDecimal("20050"))).isEqualTo("TARGET"); // touch counts
        assertThat(ce.indexExitReason(new BigDecimal("19965"))).isEqualTo("STOP_LOSS");
        assertThat(ce.indexExitReason(new BigDecimal("19970"))).isEqualTo("STOP_LOSS");
        assertThat(ce.indexExitReason(new BigDecimal("20000"))).isNull();             // inside range
    }

    @Test
    void peExitsAtTargetBelowAndStopAbove() {
        // PE: target 19950 (below entry index), stop 20030 (above).
        PositionEntity pe = position("PE", "20030", "19950");
        assertThat(pe.indexExitReason(new BigDecimal("19945"))).isEqualTo("TARGET");
        assertThat(pe.indexExitReason(new BigDecimal("19950"))).isEqualTo("TARGET");
        assertThat(pe.indexExitReason(new BigDecimal("20035"))).isEqualTo("STOP_LOSS");
        assertThat(pe.indexExitReason(new BigDecimal("20030"))).isEqualTo("STOP_LOSS");
        assertThat(pe.indexExitReason(new BigDecimal("20000"))).isNull();
    }

    @Test
    void premiumManagedPositionNeverUsesIndexExit() {
        PositionEntity premium = position("CE", null, null); // no index levels
        assertThat(premium.usesIndexExit()).isFalse();
        assertThat(premium.indexExitReason(new BigDecimal("99999"))).isNull();
    }
}
