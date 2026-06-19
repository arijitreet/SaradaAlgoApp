package com.sarada.trading.orders.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor
public class OrderEntity {

    public enum Status { NEW, SUBMITTED, FILLED, REJECTED, CANCELLED }

    public enum Side { BUY, SELL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "broker_order_id")
    private String brokerOrderId;

    @Column(name = "trading_day", nullable = false)
    private LocalDate tradingDay;

    @Column(name = "instrument_token", nullable = false)
    private long instrumentToken;

    @Column(nullable = false)
    private String tradingsymbol;

    @Column(nullable = false)
    private String exchange;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Side side;

    @Column(name = "order_type", nullable = false)
    private String orderType;

    @Column(nullable = false)
    private int quantity;

    private BigDecimal price;

    @Column(name = "avg_fill_price")
    private BigDecimal avgFillPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "status_message")
    private String statusMessage;

    @Column(nullable = false)
    private String mode;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    @Column(name = "filled_at")
    private Instant filledAt;

    public static OrderEntity create(LocalDate tradingDay, long instrumentToken, String tradingsymbol,
                                     String exchange, Side side, int quantity, String mode) {
        OrderEntity order = new OrderEntity();
        order.tradingDay = tradingDay;
        order.instrumentToken = instrumentToken;
        order.tradingsymbol = tradingsymbol;
        order.exchange = exchange;
        order.side = side;
        order.orderType = "MARKET";
        order.quantity = quantity;
        order.status = Status.NEW;
        order.mode = mode;
        order.placedAt = Instant.now();
        return order;
    }

    public void markFilled(String brokerOrderId, BigDecimal avgFillPrice) {
        this.brokerOrderId = brokerOrderId;
        this.avgFillPrice = avgFillPrice;
        this.status = Status.FILLED;
        this.statusMessage = "FILLED";
        this.filledAt = Instant.now();
    }

    public void markRejected(String brokerOrderId, String message) {
        this.brokerOrderId = brokerOrderId;
        this.status = Status.REJECTED;
        this.statusMessage = message;
    }
}
