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
                        5, 6, 2, 0, LocalTime.of(14, 30)),   // max-trades-per-day = 6, max-concurrent-trades = 2
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
        when(stats.tryReserveSlot("mean-reversion-v1", 2))
                .thenReturn(TradeStatsPort.SlotReservation.granted());
        assertThat(riskManager.evaluateEntry(signal("mean-reversion-v1")).approved()).isTrue();
    }

    @Test
    void rejectsSixthPlusTradeForEveryStrategy() {
        when(stats.tradesOpenedOn(Mockito.any())).thenReturn(6);   // global count = 6
        for (String id : new String[]{"first-candle-breakout-v1", "supertrend-flip-v1",
                "multi-confluence-trend-v1", "mean-reversion-v1"}) {
            RiskManager.Decision d = riskManager.evaluateEntry(signal(id));
            assertThat(d.approved()).as("strategy %s must be blocked at 6/6", id).isFalse();
            assertThat(d.reason()).contains("Daily trade limit reached");
        }
        // Daily limit gate rejects before the concurrent-trade slot is ever touched.
        Mockito.verify(stats, Mockito.never()).tryReserveSlot(Mockito.anyString(), Mockito.anyInt());
    }

    @Test
    void rejectsAtMaxConcurrentTrades() {
        when(stats.tradesOpenedOn(Mockito.any())).thenReturn(0);
        // Both slots taken by OTHER strategies — not a same-strategy block, a capacity block.
        when(stats.tryReserveSlot("mean-reversion-v1", 2))
                .thenReturn(TradeStatsPort.SlotReservation.blockedByCap());
        RiskManager.Decision d = riskManager.evaluateEntry(signal("mean-reversion-v1"));
        assertThat(d.approved()).isFalse();
        assertThat(d.reason()).contains("Maximum concurrent trades reached (2/2)");
    }

    @Test
    void approvesSecondConcurrentTradeWhenOneSlotFree() {
        when(stats.tradesOpenedOn(Mockito.any())).thenReturn(1);
        when(stats.tryReserveSlot("supertrend-flip-v1", 2))
                .thenReturn(TradeStatsPort.SlotReservation.granted());   // 1 open (different strategy), 1 free slot
        RiskManager.Decision d = riskManager.evaluateEntry(signal("supertrend-flip-v1"));
        assertThat(d.approved()).isTrue();
    }

    @Test
    void rejectsReEntryWhileSameStrategyAlreadyActive() {
        when(stats.tradesOpenedOn(Mockito.any())).thenReturn(1);
        when(stats.tryReserveSlot("first-candle-breakout-v1", 2))
                .thenReturn(TradeStatsPort.SlotReservation.blockedBySameStrategy(42L));
        RiskManager.Decision d = riskManager.evaluateEntry(signal("first-candle-breakout-v1"));
        assertThat(d.approved()).isFalse();
        assertThat(d.reason())
                .contains("ENTRY BLOCKED")
                .contains("strategy=first-candle-breakout-v1")
                .contains("tradeId=42");
    }

    @Test
    void rejectsReEntryWhileSameStrategyEntryStillInFlight() {
        // Blocking trade hasn't filled yet (no id known) — message must not fabricate one.
        when(stats.tradesOpenedOn(Mockito.any())).thenReturn(0);
        when(stats.tryReserveSlot("first-candle-breakout-v1", 2))
                .thenReturn(TradeStatsPort.SlotReservation.blockedBySameStrategy(null));
        RiskManager.Decision d = riskManager.evaluateEntry(signal("first-candle-breakout-v1"));
        assertThat(d.approved()).isFalse();
        assertThat(d.reason()).contains("ENTRY BLOCKED").contains("entry in flight");
    }

    @Test
    void releaseReservedSlotDelegatesToTradeStatsPort() {
        riskManager.releaseReservedSlot("first-candle-breakout-v1");
        Mockito.verify(stats).releaseSlot("first-candle-breakout-v1");
    }
}
