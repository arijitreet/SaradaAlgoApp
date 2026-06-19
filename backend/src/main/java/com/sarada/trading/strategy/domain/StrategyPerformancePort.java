package com.sarada.trading.strategy.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Port the strategy module uses to read per-strategy trading performance without
 * importing the positions module's infrastructure. Implemented by the positions
 * module (mirrors the existing {@code risk.domain.TradeStatsPort} pattern).
 */
public interface StrategyPerformancePort {

    /** Realized + unrealized P&L, trade count, and any open position for one strategy on a day. */
    StrategyPnl pnlFor(String strategyId, LocalDate day);

    record StrategyPnl(
            BigDecimal realized,
            BigDecimal unrealized,
            int trades,
            OpenPositionBrief openPosition   // null when flat
    ) {}

    record OpenPositionBrief(
            String tradingsymbol,
            String optionType,
            BigDecimal strike,
            BigDecimal unrealizedPnl
    ) {}
}
