package com.sarada.trading.common.config;

import com.sarada.trading.strategy.application.SupertrendConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Read-only view of the effective runtime configuration for the Settings page.
 * Values are changed via environment/deployment, never via the API — config
 * drift between UI and engine is impossible by construction.
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final AppProperties props;
    private final SupertrendConfigService supertrendConfig;

    @GetMapping
    public Map<String, Object> effectiveSettings() {
        var st = supertrendConfig.current();
        return Map.of(
                "brokerMode", props.brokerMode(),
                "trading", Map.of(
                        "underlying", props.trading().underlying(),
                        "lotSize", props.trading().lotSize(),
                        "quantityLots", props.trading().quantityLots(),
                        "sessionStart", props.trading().sessionStart().toString(),
                        "sessionEnd", props.trading().sessionEnd().toString(),
                        "candleMinutes", props.trading().candleMinutes(),
                        "maxTradesPerDay", props.trading().maxTradesPerDay()),
                "risk", Map.of(
                        "stopLossPoints", props.risk().stopLossPoints(),
                        "target1Points", props.risk().target1Points(),
                        "target2Points", props.risk().target2Points(),
                        "target2StopOffset", props.risk().target2StopOffset(),
                        "trailStepPoints", props.risk().trailStepPoints()),
                "strategy", Map.of(
                        "firstCandleBreakout", Map.of(
                                "emaFast", props.strategy().firstCandleBreakout().emaFast(),
                                "emaSlow", props.strategy().firstCandleBreakout().emaSlow(),
                                "atrPeriod", props.strategy().firstCandleBreakout().atrPeriod(),
                                "minAtrPoints", props.strategy().firstCandleBreakout().minAtrPoints(),
                                "srLookback", props.strategy().firstCandleBreakout().srLookback()),
                        "supertrendFlip", Map.of(
                                "atrPeriod", st.atrPeriod(),
                                "multiplier", st.multiplier(),
                                "strikeOffset", st.strikeOffset())));
    }
}
