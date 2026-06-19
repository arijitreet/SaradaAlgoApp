package com.sarada.trading.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Typed view over every `app.*` property. All secrets arrive via environment
 * variables — see .env.example. Nothing here is hardcoded.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String timezone,
        String brokerMode,
        Security security,
        Kite kite,
        Trading trading,
        Risk risk,
        Strategy strategy
) {
    public ZoneId zone() {
        return ZoneId.of(timezone);
    }

    public record Security(
            String jwtSecret,
            long jwtTtlMinutes,
            String cryptoKey,
            String adminUsername,
            String adminPassword,
            String corsOrigins
    ) {}

    public record Kite(String apiKey, String apiSecret, String redirectUrl) {}

    public record Trading(
            String underlying,
            String underlyingExchange,
            String optionExchange,
            int lotSize,
            int quantityLots,
            int strikeStep,
            LocalTime sessionStart,
            LocalTime sessionEnd,
            LocalTime firstCandleStart,
            int candleMinutes,
            int maxTradesPerDay,
            int expiryRolloverDays,
            LocalTime expiryRolloverCutoff
    ) {
        public int orderQuantity() {
            return lotSize * quantityLots;
        }
    }

    public record Risk(
            BigDecimal stopLossPoints,
            BigDecimal target1Points,
            BigDecimal target2Points,
            BigDecimal target2StopOffset,
            BigDecimal trailStepPoints
    ) {}

    public record Strategy(
            FirstCandleBreakout firstCandleBreakout,
            SupertrendFlip supertrendFlip
    ) {
        public record FirstCandleBreakout(
                int emaFast,
                int emaSlow,
                int atrPeriod,
                BigDecimal minAtrPoints,
                int srLookback
        ) {}

        public record SupertrendFlip(
                int atrPeriod,
                BigDecimal multiplier,
                int strikeOffset   // 0 = nearest ITM/ATM, 1 = 1 strike OTM, etc.
        ) {}
    }
}
