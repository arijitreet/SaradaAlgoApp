package com.sarada.trading.common.market;

import java.math.BigDecimal;

/** Point-in-time indicator values published with each closed candle / signal. */
public record IndicatorSnapshot(
        BigDecimal emaFast,
        BigDecimal emaSlow,
        BigDecimal vwap,
        BigDecimal atr,
        BigDecimal support,
        BigDecimal resistance,
        BigDecimal firstCandleHigh,
        BigDecimal firstCandleLow,
        // Supertrend-specific (null for non-supertrend strategies)
        BigDecimal supertrendLine,
        Boolean supertrendBullish
) {}
