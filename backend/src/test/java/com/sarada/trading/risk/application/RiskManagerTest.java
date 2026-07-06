package com.sarada.trading.risk.application;

import com.sarada.trading.common.audit.AuditService;
import com.sarada.trading.common.config.AppProperties;
import com.sarada.trading.common.market.SignalType;
import com.sarada.trading.common.market.TradeSignal;
import com.sarada.trading.common.time.TradingClock;
import com.sarada.trading.risk.domain.TradeStatsPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Cross-cutting risk gate: global daily trade limit shared across ALL strategies. */
class RiskManagerTest {

    private TradeStatsPort stats;
    private RiskManager riskManager;

    @BeforeEach
    void setUp() {
        stats = Mockito.mock(TradeStatsPort.class);
        TradingClock clock = Mockito.mock(TradingClock.class);
        AuditService audit = Mockito.mock(AuditService.class);
        AppProperties props = new AppProperties(
                "Asia/Kolkata", "PAPER", null, null,
                new AppProperties.Trading(
                        "NIFTY 50", "NSE", "NFO", 65, 1, 50,
                        LocalTime.of(9, 20), LocalTime.of(15, 5), LocalTime.of(9, 15),
                        5, 6, 0, LocalTime.of(14, 30)),   // max-trades-per-day = 6 (global)
                new AppProperties.Risk(new BigDecimal("25"), new BigDecimal("25"),
                        new BigDecimal("50"), new BigDecimal("30"), new BigDecimal("25")),
                null);
        when(clock.isWithinSession()).thenReturn(true);
        when(clock.tradingDay()).thenReturn(LocalDate.of(2026, 7, 6));
        riskManager = new RiskManager(stats, clock, props, audit);
    }

    private TradeSignal signal(String strategyId) {
        return new TradeSignal(strategyId, SignalType.BUY_CE, "NIFTY 50",
                new BigDecimal("24000"), null, "test", Instant.now(), 0, null, null);
    }

    @Test
    void approvesWhenUnderGlobalLimitAndFlat() {
        when(stats.tradesOpenedOn(Mockito.any())).thenReturn(5);
        when(stats.hasOpenPosition()).thenReturn(false);
        assertThat(riskManager.evaluateEntry(signal("mean-reversion-v1")).approved()).isTrue();
    }

    @Test
    void rejectsSixthPlusTradeForEveryStrategy() {
        when(stats.tradesOpenedOn(Mockito.any())).thenReturn(6);   // global count = 6
        when(stats.hasOpenPosition()).thenReturn(false);
        for (String id : new String[]{"first-candle-breakout-v1", "supertrend-flip-v1",
                "multi-confluence-trend-v1", "mean-reversion-v1"}) {
            RiskManager.Decision d = riskManager.evaluateEntry(signal(id));
            assertThat(d.approved()).as("strategy %s must be blocked at 6/6", id).isFalse();
            assertThat(d.reason()).contains("Daily trade limit reached");
        }
    }

    @Test
    void rejectsWhenAPositionIsAlreadyOpen() {
        when(stats.tradesOpenedOn(Mockito.any())).thenReturn(0);
        when(stats.hasOpenPosition()).thenReturn(true);
        RiskManager.Decision d = riskManager.evaluateEntry(signal("mean-reversion-v1"));
        assertThat(d.approved()).isFalse();
        assertThat(d.reason()).contains("already open");
    }
}
