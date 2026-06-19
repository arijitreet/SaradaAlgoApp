package com.sarada.trading.risk.domain;

import java.time.LocalDate;

/** What risk needs to know about executed trades — implemented by the positions module. */
public interface TradeStatsPort {

    int tradesOpenedOn(LocalDate tradingDay);

    boolean hasOpenPosition();
}
