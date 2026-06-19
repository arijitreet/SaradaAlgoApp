package com.sarada.trading.strategy.domain;

import com.sarada.trading.common.market.TradeSignal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "signals")
@Getter
@NoArgsConstructor
public class SignalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "strategy_id", nullable = false)
    private String strategyId;

    @Column(name = "trading_day", nullable = false)
    private LocalDate tradingDay;

    @Column(name = "signal_type", nullable = false)
    private String signalType;

    @Column(nullable = false)
    private String underlying;

    @Column(name = "trigger_price", nullable = false)
    private BigDecimal triggerPrice;

    @Column(name = "ema_fast")
    private BigDecimal emaFast;

    @Column(name = "ema_slow")
    private BigDecimal emaSlow;

    private BigDecimal vwap;

    private BigDecimal atr;

    private String reason;

    @Setter
    @Column(nullable = false)
    private boolean accepted;

    @Setter
    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static SignalEntity from(TradeSignal signal, LocalDate tradingDay) {
        SignalEntity entity = new SignalEntity();
        entity.strategyId = signal.strategyId();
        entity.tradingDay = tradingDay;
        entity.signalType = signal.type().name();
        entity.underlying = signal.underlying();
        entity.triggerPrice = signal.triggerPrice();
        entity.emaFast = signal.indicators().emaFast();
        entity.emaSlow = signal.indicators().emaSlow();
        entity.vwap = signal.indicators().vwap();
        entity.atr = signal.indicators().atr();
        entity.reason = signal.reason();
        entity.accepted = false;
        entity.createdAt = Instant.now();
        return entity;
    }
}
