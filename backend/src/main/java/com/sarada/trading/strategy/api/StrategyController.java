package com.sarada.trading.strategy.api;

import com.sarada.trading.common.config.AppProperties;
import com.sarada.trading.common.time.TradingClock;
import com.sarada.trading.strategy.application.StrategyEngine;
import com.sarada.trading.strategy.application.SupertrendConfigService;
import com.sarada.trading.strategy.application.TradingSessionService;
import com.sarada.trading.strategy.domain.SignalEntity;
import com.sarada.trading.strategy.domain.StrategyPerformancePort;
import com.sarada.trading.strategy.domain.TradingStrategy;
import com.sarada.trading.strategy.infra.SignalRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/strategy")
@RequiredArgsConstructor
@Validated
public class StrategyController {

    private final StrategyEngine engine;
    private final SignalRepository signals;
    private final TradingClock clock;
    private final SupertrendConfigService supertrendConfig;
    private final StrategyPerformancePort performance;
    private final TradingSessionService session;
    private final AppProperties props;
    private final com.sarada.trading.strategy.application.StrategyTimeWindows timeWindows;

    public record StrategyView(String id, String underlying,
                               com.sarada.trading.common.market.IndicatorSnapshot indicators,
                               TradingStrategy.Health health) {}

    @GetMapping
    public List<StrategyView> strategies() {
        return engine.activeStrategies().stream()
                .map(s -> new StrategyView(s.id(), s.underlying(), s.snapshot(), s.health()))
                .toList();
    }

    @GetMapping("/signals")
    public List<SignalEntity> todaysSignals() {
        return signals.findByTradingDayOrderByCreatedAtDesc(clock.tradingDay());
    }

    // ── Supertrend live config ──────────────────────────────────────────────

    @GetMapping("/supertrend-config")
    public SupertrendConfigService.SupertrendConfig supertrendConfig() {
        return supertrendConfig.current();
    }

    public record SupertrendConfigUpdate(
            @Min(2) @Max(50) int atrPeriod,
            @NotNull @DecimalMin("1.0") @DecimalMax("6.0") BigDecimal multiplier,
            @Min(0) @Max(5) int strikeOffset
    ) {}

    @PostMapping("/supertrend-config")
    public SupertrendConfigService.SupertrendConfig updateSupertrendConfig(
            @Valid @RequestBody SupertrendConfigUpdate body, Principal principal) {
        return supertrendConfig.update(body.atrPeriod(), body.multiplier(), body.strikeOffset(),
                principal != null ? principal.getName() : "SYSTEM");
    }

    // ── per-strategy performance (comparison view) ──────────────────────────

    public record LastSignalView(String type, BigDecimal triggerPrice, Instant at) {}

    public record StrategyPerformanceView(
            String strategyId, boolean active,
            BigDecimal realizedPnl, BigDecimal unrealizedPnl, BigDecimal totalPnl,
            int trades, int maxTrades,
            StrategyPerformancePort.OpenPositionBrief openPosition,
            LastSignalView lastSignal,
            String activeWindow, boolean windowActive) {}

    @GetMapping("/performance")
    public List<StrategyPerformanceView> performance() {
        var day = clock.tradingDay();
        boolean active = session.isRunning();
        int maxTrades = props.trading().maxTradesPerDay();
        return engine.activeStrategies().stream()
                .map(s -> {
                    StrategyPerformancePort.StrategyPnl pnl = performance.pnlFor(s.id(), day);
                    LastSignalView last = signals
                            .findFirstByStrategyIdAndTradingDayOrderByCreatedAtDesc(s.id(), day)
                            .map(sig -> new LastSignalView(sig.getSignalType(), sig.getTriggerPrice(),
                                    sig.getCreatedAt()))
                            .orElse(null);
                    return new StrategyPerformanceView(
                            s.id(), active,
                            pnl.realized(), pnl.unrealized(), pnl.realized().add(pnl.unrealized()),
                            pnl.trades(), maxTrades, pnl.openPosition(), last,
                            timeWindows.windowLabel(s.id()), timeWindows.isActive(s.id()));
                })
                .toList();
    }
}
