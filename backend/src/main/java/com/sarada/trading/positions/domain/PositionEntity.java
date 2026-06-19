package com.sarada.trading.positions.domain;

import com.sarada.trading.risk.domain.TrailingStopPolicy;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "positions")
@Getter
@NoArgsConstructor
public class PositionEntity {

    public enum Status { OPEN, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trading_day", nullable = false)
    private LocalDate tradingDay;

    @Column(name = "strategy_id", nullable = false)
    private String strategyId;

    @Column(name = "signal_id")
    private Long signalId;

    @Column(name = "entry_order_id")
    private Long entryOrderId;

    @Column(name = "exit_order_id")
    private Long exitOrderId;

    @Column(name = "instrument_token", nullable = false)
    private long instrumentToken;

    @Column(nullable = false)
    private String tradingsymbol;

    @Column(name = "option_type", nullable = false)
    private String optionType;

    @Column(nullable = false)
    private BigDecimal strike;

    @Column(nullable = false)
    private LocalDate expiry;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "entry_price", nullable = false)
    private BigDecimal entryPrice;

    @Column(name = "stop_loss", nullable = false)
    private BigDecimal stopLoss;

    @Column(nullable = false)
    private BigDecimal target1;

    @Column(nullable = false)
    private BigDecimal target2;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_stage", nullable = false)
    private TrailingStopPolicy.Stage riskStage = TrailingStopPolicy.Stage.INITIAL;

    @Column(name = "exit_price")
    private BigDecimal exitPrice;

    @Column(name = "exit_reason")
    private String exitReason;

    @Column(name = "realized_pnl")
    private BigDecimal realizedPnl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    public static PositionEntity open(LocalDate tradingDay, String strategyId, Long signalId,
                                      Long entryOrderId, long instrumentToken, String tradingsymbol,
                                      String optionType, BigDecimal strike, LocalDate expiry,
                                      int quantity, BigDecimal entryPrice,
                                      BigDecimal stopLoss, BigDecimal target1, BigDecimal target2) {
        PositionEntity position = new PositionEntity();
        position.tradingDay = tradingDay;
        position.strategyId = strategyId;
        position.signalId = signalId;
        position.entryOrderId = entryOrderId;
        position.instrumentToken = instrumentToken;
        position.tradingsymbol = tradingsymbol;
        position.optionType = optionType;
        position.strike = strike;
        position.expiry = expiry;
        position.quantity = quantity;
        position.entryPrice = entryPrice;
        position.stopLoss = stopLoss;
        position.target1 = target1;
        position.target2 = target2;
        position.openedAt = Instant.now();
        return position;
    }

    public void updateStop(BigDecimal newStop, TrailingStopPolicy.Stage stage) {
        this.stopLoss = newStop;
        this.riskStage = stage;
    }

    public void close(Long exitOrderId, BigDecimal exitPrice, String exitReason) {
        this.exitOrderId = exitOrderId;
        this.exitPrice = exitPrice;
        this.exitReason = exitReason;
        this.realizedPnl = exitPrice.subtract(entryPrice).multiply(BigDecimal.valueOf(quantity));
        this.status = Status.CLOSED;
        this.closedAt = Instant.now();
    }

    public BigDecimal unrealizedPnl(BigDecimal lastPrice) {
        return lastPrice.subtract(entryPrice).multiply(BigDecimal.valueOf(quantity));
    }
}
