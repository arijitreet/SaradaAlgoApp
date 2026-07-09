package com.sarada.trading.orders.domain;

import com.sarada.trading.common.market.IndicatorSnapshot;
import com.sarada.trading.common.market.SignalType;
import com.sarada.trading.common.market.TradeSignal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "pending_retry")
@Getter
@NoArgsConstructor
public class PendingRetryEntity {

    public enum Status { WAITING, CONSUMED, EXPIRED }

    private static final IndicatorSnapshot EMPTY_SNAPSHOT =
            new IndicatorSnapshot(null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null);

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

    @Column(name = "strike_offset", nullable = false)
    private int strikeOffset;

    @Column(name = "index_stop_loss")
    private BigDecimal indexStopLoss;

    @Column(name = "index_target")
    private BigDecimal indexTarget;

    @Column(name = "reject_reason", length = 512)
    private String rejectReason;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Setter
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PendingRetryEntity from(TradeSignal signal, LocalDate tradingDay, String rejectReason) {
        PendingRetryEntity entity = new PendingRetryEntity();
        entity.strategyId = signal.strategyId();
        entity.tradingDay = tradingDay;
        entity.signalType = signal.type().name();
        entity.underlying = signal.underlying();
        entity.triggerPrice = signal.triggerPrice();
        entity.strikeOffset = signal.strikeOffset();
        entity.indexStopLoss = signal.indexStopLoss();
        entity.indexTarget = signal.indexTarget();
        entity.rejectReason = rejectReason;
        entity.status = Status.WAITING;
        Instant now = Instant.now();
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    /** Reconstructs the original TradeSignal for re-submission through the normal entry gate. */
    public TradeSignal toSignal() {
        return new TradeSignal(
                strategyId,
                SignalType.valueOf(signalType),
                underlying,
                triggerPrice,
                EMPTY_SNAPSHOT,
                "RETRY (originally @ " + triggerPrice + " — funds rejection)",
                createdAt,
                strikeOffset,
                indexStopLoss,
                indexTarget
        );
    }
}
