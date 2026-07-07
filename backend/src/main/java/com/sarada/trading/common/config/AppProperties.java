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
            int maxConcurrentTrades,
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
            SupertrendFlip supertrendFlip,
            MultiConfluenceTrend multiConfluenceTrend,
            MeanReversion meanReversion
    ) {
        public record FirstCandleBreakout(
                int emaFast,
                int emaSlow,
                int atrPeriod,
                BigDecimal minAtrPoints,
                int srLookback,
                BigDecimal stopLossPoints
        ) {}

        public record SupertrendFlip(
                int atrPeriod,
                BigDecimal multiplier,
                int strikeOffset,
                BigDecimal stopLossPoints,
                BigDecimal target1Points,
                BigDecimal target2Points,
                BigDecimal target2StopOffset,
                BigDecimal trailStepPoints
        ) {
            public Risk toRisk() {
                return new Risk(stopLossPoints, target1Points, target2Points,
                        target2StopOffset, trailStepPoints);
            }
        }

        public record MultiConfluenceTrend(
                int emaFast,            // 9 EMA on 5-min closes (base crossover)
                int emaSlow,            // 21 EMA on 5-min closes
                int atrPeriod,          // Supertrend ATR period
                BigDecimal multiplier,  // Supertrend band multiplier
                int htfEmaFast,         // 9 EMA on 15-min closes (higher-timeframe trend)
                int htfEmaSlow,         // 21 EMA on 15-min closes
                int strikeOffset,
                BigDecimal stopLossPoints
        ) {}

        public record MeanReversion(
                int bbPeriod,               // Bollinger SMA period
                BigDecimal bbStdDev,        // Bollinger band width in standard deviations
                int rsiPeriod,
                BigDecimal rsiOversold,     // BUY when RSI below this
                BigDecimal rsiOverbought,   // SELL when RSI above this
                int adxPeriod,
                BigDecimal adxThreshold,    // only trade when ADX below this (ranging market)
                BigDecimal slBbWidthMult,   // index SL distance = mult × BB width at entry
                int strikeOffset
        ) {}
    }
}
