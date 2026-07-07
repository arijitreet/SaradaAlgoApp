package com.sarada.trading.risk.domain;

import java.time.LocalDate;

/** What risk needs to know about executed trades — implemented by the positions module. */
public interface TradeStatsPort {

    int tradesOpenedOn(LocalDate tradingDay);

    /** Outcome of {@link #tryReserveSlot}, with enough detail for a clear rejection message. */
    record SlotReservation(boolean approved, boolean blockedBySameStrategy, Long activeTradeId) {
        public static SlotReservation granted() {
            return new SlotReservation(true, false, null);
        }

        /** {@code activeTradeId} is null when the blocking trade is still in-flight
         *  (signal approved but its order hasn't filled yet, so it has no id). */
        public static SlotReservation blockedBySameStrategy(Long activeTradeId) {
            return new SlotReservation(false, true, activeTradeId);
        }

        public static SlotReservation blockedByCap() {
            return new SlotReservation(false, false, null);
        }
    }

    /**
     * Atomically checks, for the given strategy, that (a) it has no active/reserved trade of
     * its own and (b) the global active/reserved count is under the given cap — and if both
     * hold, reserves a slot for it (in the same operation) so two signals evaluated
     * concurrently can never both reserve the last slot, or both slip past the same-strategy
     * check. The slot must later be released via {@link #releaseSlot} if the entry pipeline
     * aborts before the position opens, or when the resulting position is closed.
     */
    SlotReservation tryReserveSlot(String strategyId, int maxConcurrentTrades);

    /** Releases the slot reserved via {@link #tryReserveSlot} for this strategy. */
    void releaseSlot(String strategyId);
}
