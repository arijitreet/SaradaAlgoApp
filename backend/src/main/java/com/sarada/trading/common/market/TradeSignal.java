package com.sarada.trading.common.market;

import java.math.BigDecimal;
import java.time.Instant;

/** A strategy's intent to trade — risk management decides whether it executes. */
public record TradeSignal(
        String strategyId,
        SignalType type,
        String underlying,
        BigDecimal triggerPrice,
        IndicatorSnapshot indicators,
        String reason,
        Instant at,
        int strikeOffset   // 0 = nearest ITM/ATM, 1 = 1-strike OTM, etc.
) {}
