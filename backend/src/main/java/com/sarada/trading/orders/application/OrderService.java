package com.sarada.trading.orders.application;

import com.sarada.trading.broker.domain.BrokerGateway;
import com.sarada.trading.common.audit.AuditService;
import com.sarada.trading.common.time.TradingClock;
import com.sarada.trading.common.ws.WsPublisher;
import com.sarada.trading.orders.domain.OrderEntity;
import com.sarada.trading.orders.infra.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Places MARKET orders through the active BrokerGateway (live or paper) and
 * records the full lifecycle. Returns the persisted order either FILLED or
 * REJECTED — callers never deal with broker SDKs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final BrokerGateway broker;
    private final OrderRepository orders;
    private final TradingClock clock;
    private final AuditService audit;
    private final WsPublisher ws;

    public OrderEntity placeMarket(long instrumentToken, String tradingsymbol, String exchange,
                                   OrderEntity.Side side, int quantity) {
        OrderEntity order = OrderEntity.create(clock.tradingDay(), instrumentToken, tradingsymbol,
                exchange, side, quantity, broker.mode());
        order = orders.save(order);

        BrokerGateway.OrderResult result = broker.placeMarketOrder(new BrokerGateway.OrderRequest(
                instrumentToken, exchange, tradingsymbol,
                side == OrderEntity.Side.BUY
                        ? BrokerGateway.OrderRequest.Side.BUY
                        : BrokerGateway.OrderRequest.Side.SELL,
                quantity));

        if (result.filled()) {
            order.markFilled(result.brokerOrderId(), result.avgFillPrice());
            audit.log("ORDER", "FILLED", side + " " + quantity + " " + tradingsymbol
                    + " @ " + result.avgFillPrice() + " [" + broker.mode() + "]");
        } else {
            order.markRejected(result.brokerOrderId(), result.message());
            audit.log("ORDER", "REJECTED", side + " " + tradingsymbol + " — " + result.message());
        }
        order = orders.save(order);

        ws.publish(WsPublisher.TOPIC_ORDERS, order);
        String feedDetail = order.getStatus() == OrderEntity.Status.FILLED
                ? "FILLED @ " + order.getAvgFillPrice()
                : "REJECTED" + (order.getStatusMessage() != null ? " — " + order.getStatusMessage() : "");
        ws.feed("ORDER", side + " " + tradingsymbol, feedDetail);
        return order;
    }
}
