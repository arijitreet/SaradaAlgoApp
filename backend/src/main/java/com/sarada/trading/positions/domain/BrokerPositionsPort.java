package com.sarada.trading.positions.domain;

import java.util.Map;
import java.util.Optional;

/**
 * Read-only port for reconciling locally-tracked open positions against the
 * broker's books after a restart. Implemented by the broker module (Kite).
 * Strictly informational — recovery never places, modifies, or cancels orders.
 */
public interface BrokerPositionsPort {

    /**
     * Net open quantity per tradingsymbol from the broker's position book,
     * or empty when the broker is unreachable/unauthenticated (caller should
     * skip reconciliation and retry on the next connect).
     */
    Optional<Map<String, Integer>> openNetQuantities();
}
