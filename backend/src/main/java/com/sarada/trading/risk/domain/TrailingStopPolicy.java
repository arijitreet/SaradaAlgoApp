package com.sarada.trading.risk.domain;

import com.sarada.trading.common.config.AppProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure stop-loss ladder (all values are option-premium points):
 *
 *   entry          → SL = entry − 25
 *   gain ≥ +25 (T1) → SL = entry          (breakeven)
 *   gain ≥ +50 (T2) → SL = entry + 30     (locked profit)
 *   each further +25 → SL trails up by +25 (entry+55, entry+80, …)
 *
 * The stop never moves down.
 */
@Component
public class TrailingStopPolicy {

    public enum Stage { INITIAL, BREAKEVEN, LOCKED, TRAILING }

    private final AppProperties.Risk risk;

    public TrailingStopPolicy(AppProperties props) {
        this.risk = props.risk();
    }

    public record StopState(BigDecimal stopLoss, Stage stage) {}

    public StopState initial(BigDecimal entryPrice) {
        return new StopState(entryPrice.subtract(risk.stopLossPoints()), Stage.INITIAL);
    }

    public BigDecimal target1(BigDecimal entryPrice) {
        return entryPrice.add(risk.target1Points());
    }

    public BigDecimal target2(BigDecimal entryPrice) {
        return entryPrice.add(risk.target2Points());
    }

    /** Recompute the stop for the current price; result is monotonically non-decreasing. */
    public StopState advance(BigDecimal entryPrice, BigDecimal currentPrice,
                             BigDecimal currentStop, Stage currentStage) {
        BigDecimal gain = currentPrice.subtract(entryPrice);

        BigDecimal candidateStop = currentStop;
        Stage candidateStage = currentStage;

        if (gain.compareTo(risk.target2Points()) >= 0) {
            long extraSteps = gain.subtract(risk.target2Points())
                    .divide(risk.trailStepPoints(), 0, RoundingMode.FLOOR)
                    .longValue();
            candidateStop = entryPrice.add(risk.target2StopOffset())
                    .add(risk.trailStepPoints().multiply(BigDecimal.valueOf(extraSteps)));
            candidateStage = extraSteps > 0 ? Stage.TRAILING : Stage.LOCKED;
        } else if (gain.compareTo(risk.target1Points()) >= 0) {
            candidateStop = entryPrice;
            candidateStage = currentStage == Stage.INITIAL ? Stage.BREAKEVEN : currentStage;
        }

        if (candidateStop.compareTo(currentStop) <= 0) {
            return new StopState(currentStop, currentStage);
        }
        return new StopState(candidateStop, candidateStage);
    }

    public boolean isStopHit(BigDecimal currentPrice, BigDecimal stopLoss) {
        return currentPrice.compareTo(stopLoss) <= 0;
    }
}
