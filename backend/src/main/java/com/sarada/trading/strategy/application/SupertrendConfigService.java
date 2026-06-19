package com.sarada.trading.strategy.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarada.trading.common.audit.AuditService;
import com.sarada.trading.common.config.AppProperties;
import com.sarada.trading.common.error.DomainException;
import com.sarada.trading.common.events.DomainEvents;
import com.sarada.trading.common.settings.AppSettingEntity;
import com.sarada.trading.common.settings.AppSettingRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mutable, persisted runtime config for the Supertrend Flip strategy.
 *
 * Seeded from application.yml at boot, then overlaid with any persisted override
 * stored in {@code app_settings} (single JSON value). Updates persist + swap the
 * in-memory value atomically and publish {@link DomainEvents.StrategyConfigChanged}
 * so the live strategy bean can rebuild its indicator. A persisted override takes
 * precedence over application.yml on the next restart.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupertrendConfigService {

    private static final String KEY = "strategy:supertrend-flip";
    private static final BigDecimal MIN_MULT = BigDecimal.ONE;
    private static final BigDecimal MAX_MULT = BigDecimal.valueOf(6);

    private final AppProperties props;
    private final AppSettingRepository settings;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final ObjectMapper json;

    private final AtomicReference<SupertrendConfig> current = new AtomicReference<>();

    public record SupertrendConfig(int atrPeriod, BigDecimal multiplier, int strikeOffset) {}

    @PostConstruct
    void load() {
        SupertrendConfig effective = settings.findById(KEY)
                .map(AppSettingEntity::getValue)
                .map(this::parse)
                .orElseGet(this::defaults);
        current.set(effective);
        log.info("Supertrend config loaded: ATR={} mult={} strikeOffset={} ({})",
                effective.atrPeriod(), effective.multiplier(), effective.strikeOffset(),
                settings.existsById(KEY) ? "persisted override" : "application.yml defaults");
    }

    public SupertrendConfig current() {
        return current.get();
    }

    public SupertrendConfig update(int atrPeriod, BigDecimal multiplier, int strikeOffset, String actor) {
        if (atrPeriod < 2 || atrPeriod > 50) {
            throw new DomainException("ATR period must be between 2 and 50");
        }
        if (multiplier.compareTo(MIN_MULT) < 0 || multiplier.compareTo(MAX_MULT) > 0) {
            throw new DomainException("Multiplier must be between 1.0 and 6.0");
        }
        if (strikeOffset < 0 || strikeOffset > 5) {
            throw new DomainException("Strike offset must be between 0 and 5");
        }

        SupertrendConfig previous = current.get();
        SupertrendConfig next = new SupertrendConfig(atrPeriod, multiplier, strikeOffset);
        // Only an ATR-period / multiplier change forces an indicator rebuild + warm-up reset.
        boolean indicatorChanged = previous == null
                || previous.atrPeriod() != atrPeriod
                || previous.multiplier().compareTo(multiplier) != 0;

        settings.save(new AppSettingEntity(KEY, write(next), Instant.now()));
        current.set(next);
        audit.log("STRATEGY", "CONFIG_UPDATED",
                "supertrend-flip ATR=" + atrPeriod + " mult=" + multiplier
                        + " strikeOffset=" + strikeOffset, actor);
        events.publishEvent(new DomainEvents.StrategyConfigChanged(SupertrendFlipStrategy.ID, indicatorChanged));
        log.info("Supertrend config updated by {}: ATR={} mult={} strikeOffset={} (indicatorChanged={})",
                actor, atrPeriod, multiplier, strikeOffset, indicatorChanged);
        return next;
    }

    private SupertrendConfig defaults() {
        var cfg = props.strategy().supertrendFlip();
        return new SupertrendConfig(cfg.atrPeriod(), cfg.multiplier(), cfg.strikeOffset());
    }

    private SupertrendConfig parse(String value) {
        try {
            return json.readValue(value, SupertrendConfig.class);
        } catch (Exception e) {
            log.warn("Could not parse persisted Supertrend config '{}': {} — falling back to application.yml",
                    value, e.getMessage());
            return defaults();
        }
    }

    private String write(SupertrendConfig config) {
        try {
            return json.writeValueAsString(config);
        } catch (Exception e) {
            throw new DomainException("Failed to serialize Supertrend config: " + e.getMessage());
        }
    }
}
