package com.sarada.trading.marketdata.domain;

import java.time.LocalDate;

/**
 * What strike-selection needs to know about contracts already in play, to avoid
 * resolving a signal to an option contract that's either currently ACTIVE or was
 * already taken (opened, regardless of whether it has since exited) earlier today.
 * Implemented by the positions module.
 */
public interface ActiveTradesPort {

    /** True if this exact contract (by tradingsymbol) is currently OPEN, or was
     *  opened at all on the given trading day (open or closed). DB-backed, so the
     *  check is correct immediately after a restart — not dependent on in-memory state. */
    boolean isContractInUseToday(String tradingsymbol, LocalDate tradingDay);
}
