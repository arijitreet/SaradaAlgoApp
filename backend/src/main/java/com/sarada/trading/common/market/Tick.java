package com.sarada.trading.common.market;

import java.math.BigDecimal;
import java.time.Instant;

/** Immutable market tick — shared kernel between broker feed, aggregator and monitors. */
public record Tick(
        long instrumentToken,
        BigDecimal lastPrice,
        long volumeTraded,
        Instant timestamp
) {}
