package com.sarada.trading.positions.domain;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Port the positions module uses to read the live underlying-index price for
 * index-based exits (Mean Reversion). Implemented by the marketdata module so
 * positions never reaches into marketdata internals directly.
 */
public interface UnderlyingPricePort {

    /** Latest traded price of the underlying index, or empty if no tick yet. */
    Optional<BigDecimal> underlyingLtp();
}
