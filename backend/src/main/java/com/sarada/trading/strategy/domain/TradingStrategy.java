package com.sarada.trading.strategy.domain;

import com.sarada.trading.common.market.Candle;
import com.sarada.trading.common.market.IndicatorSnapshot;
import com.sarada.trading.common.market.TradeSignal;

import java.util.Optional;

/**
 * Strategy SPI. Implementations are stateful per trading day and are driven
 * exclusively by closed candles — adding a strategy means adding a bean.
 */
public interface TradingStrategy {

    String id();

    /** Instrument this strategy trades on (multi-symbol = multiple strategy beans). */
    String underlying();

    /** Feed one closed candle; returns an entry signal when all rules align. */
    Optional<TradeSignal> onCandle(Candle candle);

    /** Current indicator values for monitoring (nullable until warmed up). */
    IndicatorSnapshot snapshot();

    /** Health flags for the dashboard's strategy health card. */
    Health health();

    /** Clear all per-day state (called on trading-day change). */
    void reset();

    record Health(
            boolean firstCandleCaptured,
            int candlesProcessed,
            boolean indicatorsReady,
            boolean emaBullish,
            boolean emaBearish,
            boolean aboveVwap,
            boolean belowVwap,
            boolean atrPass,
            String lastEvaluation
    ) {}
}
