package com.sarada.trading.broker.domain;

import java.math.BigDecimal;

/**
 * Port for order execution. Implementations: Zerodha Kite (live) and the
 * paper-trading simulator. Multi-broker support = add another adapter.
 */
public interface BrokerGateway {

    OrderResult placeMarketOrder(OrderRequest request);

    /** "PAPER" or "ZERODHA" — recorded on every order for auditability. */
    String mode();

    record OrderRequest(
            long instrumentToken,
            String exchange,
            String tradingsymbol,
            Side side,
            int quantity
    ) {
        public enum Side { BUY, SELL }
    }

    record OrderResult(
            String brokerOrderId,
            boolean filled,
            BigDecimal avgFillPrice,
            String message
    ) {
        public static OrderResult rejected(String message) {
            return new OrderResult(null, false, null, message);
        }
    }
}
