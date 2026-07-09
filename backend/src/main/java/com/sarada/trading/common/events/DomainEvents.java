package com.sarada.trading.common.events;

import com.sarada.trading.common.market.Candle;
import com.sarada.trading.common.market.Tick;
import com.sarada.trading.common.market.TradeSignal;

import java.math.BigDecimal;

/**
 * Module-to-module contracts. Modules never call each other's services directly
 * for the trading pipeline — they react to these events, which keeps the
 * dependency graph acyclic and the modules independently extractable.
 */
public final class DomainEvents {

    private DomainEvents() {}

    /** Raw tick from the active market feed (Kite ticker or paper feed). */
    public record TickReceived(Tick tick) {}

    /** A 5-minute candle on a subscribed instrument has completed. */
    public record CandleClosed(Candle candle) {}

    /** A past candle replayed during historical backfill (warms up indicators; never opens trades). */
    public record HistoricalCandleClosed(Candle candle) {}

    /** Strategy produced an entry intent; the trade orchestrator is the consumer. */
    public record SignalGenerated(long signalId, TradeSignal signal) {}

    /** Entry order fully filled; positions module opens a managed position. */
    public record EntryFilled(long orderId, TradeSignal signal, long instrumentToken,
                              String tradingsymbol, BigDecimal strike, java.time.LocalDate expiry,
                              int quantity, BigDecimal fillPrice) {}

    public record PositionOpened(long positionId) {}

    public record PositionClosed(long positionId, BigDecimal realizedPnl, String exitReason) {}

    /** Risk module demands all open positions be flattened (15:05 force exit). */
    public record ForceExitRequested(String reason) {}

    /** Trading engine started/stopped (manual or scheduled). */
    public record SessionStateChanged(boolean running, String reason) {}

    /**
     * A strategy's runtime configuration was changed via the API.
     * {@code indicatorChanged} is true when a parameter that feeds the indicator
     * (e.g. ATR period / multiplier) changed and the strategy must rebuild + re-warm.
     */
    public record StrategyConfigChanged(String strategyId, boolean indicatorChanged) {}

    /** Broker WS feed connectivity: CONNECTED / RECONNECTING / DISCONNECTED. */
    public record BrokerConnectionChanged(String state, String detail) {}

    /**
     * A signal that was previously rejected due to insufficient funds is eligible
     * for retry. Published by PendingRetryService when a position closes; consumed
     * by StrategyEngine which records a fresh SignalEntity and re-emits SignalGenerated
     * so the full normal entry gate runs again.
     */
    public record RetrySignalRequested(TradeSignal signal) {}
}
