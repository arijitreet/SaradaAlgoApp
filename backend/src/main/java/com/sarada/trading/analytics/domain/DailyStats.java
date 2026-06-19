package com.sarada.trading.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "daily_stats")
@Getter
@NoArgsConstructor
public class DailyStats {

    @Id
    @Column(name = "trading_day")
    private LocalDate tradingDay;

    @Column(nullable = false)
    private int trades;

    @Column(nullable = false)
    private int wins;

    @Column(nullable = false)
    private int losses;

    @Column(name = "gross_pnl", nullable = false)
    private BigDecimal grossPnl = BigDecimal.ZERO;

    @Column(name = "max_drawdown", nullable = false)
    private BigDecimal maxDrawdown = BigDecimal.ZERO;

    @Column(name = "best_trade", nullable = false)
    private BigDecimal bestTrade = BigDecimal.ZERO;

    @Column(name = "worst_trade", nullable = false)
    private BigDecimal worstTrade = BigDecimal.ZERO;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public DailyStats(LocalDate tradingDay) {
        this.tradingDay = tradingDay;
    }

    public void applyTrade(BigDecimal pnl) {
        trades++;
        if (pnl.signum() >= 0) {
            wins++;
        } else {
            losses++;
        }
        grossPnl = grossPnl.add(pnl);
        bestTrade = bestTrade.max(pnl);
        worstTrade = worstTrade.min(pnl);
        maxDrawdown = maxDrawdown.min(grossPnl);
        updatedAt = Instant.now();
    }
}
