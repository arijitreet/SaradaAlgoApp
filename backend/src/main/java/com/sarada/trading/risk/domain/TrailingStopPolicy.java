package com.sarada.trading.risk.domain;

import com.sarada.trading.common.config.AppProperties;
import com.sarada.trading.strategy.application.FirstCandleBreakoutStrategy;
import com.sarada.trading.strategy.application.MultiConfluenceTrendStrategy;
import com.sarada.trading.strategy.application.SupertrendFlipStrategy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class TrailingStopPolicy {

    public enum Stage { INITIAL, BREAKEVEN, LOCKED, TRAILING }

    private final AppProperties.Risk defaultRisk;
    private final AppProperties props;

    public TrailingStopPolicy(AppProperties props) {
        this.defaultRisk = props.risk();
        this.props = props;
    }

    public record StopState(BigDecimal stopLoss, Stage stage) {}

    public AppProperties.Risk riskFor(String strategyId) {
        if (SupertrendFlipStrategy.ID.equals(strategyId)) {
            return props.strategy().supertrendFlip().toRisk();
        }
        if (FirstCandleBreakoutStrategy.ID.equals(strategyId)) {
            // Initial SL only — target1/target2/target2StopOffset/trailStepPoints stay shared defaults.
            return new AppProperties.Risk(props.strategy().firstCandleBreakout().stopLossPoints(),
                    defaultRisk.target1Points(), defaultRisk.target2Points(),
                    defaultRisk.target2StopOffset(), defaultRisk.trailStepPoints());
        }
        if (MultiConfluenceTrendStrategy.ID.equals(strategyId)) {
            // Initial SL only — target1/target2/target2StopOffset/trailStepPoints stay shared defaults.
            return new AppProperties.Risk(props.strategy().multiConfluenceTrend().stopLossPoints(),
                    defaultRisk.target1Points(), defaultRisk.target2Points(),
                    defaultRisk.target2StopOffset(), defaultRisk.trailStepPoints());
        }
        return defaultRisk;
    }

    public StopState initial(BigDecimal entryPrice, String strategyId) {
        var risk = riskFor(strategyId);
        return new StopState(entryPrice.subtract(risk.stopLossPoints()), Stage.INITIAL);
    }

    public BigDecimal target1(BigDecimal entryPrice, String strategyId) {
        return entryPrice.add(riskFor(strategyId).target1Points());
    }

    public BigDecimal target2(BigDecimal entryPrice, String strategyId) {
        return entryPrice.add(riskFor(strategyId).target2Points());
    }

    public StopState advance(BigDecimal entryPrice, BigDecimal currentPrice,
                             BigDecimal currentStop, Stage currentStage, String strategyId) {
        var risk = riskFor(strategyId);
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
