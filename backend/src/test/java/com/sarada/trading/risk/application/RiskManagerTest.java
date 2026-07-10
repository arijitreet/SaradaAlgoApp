package com.sarada.trading.risk.application;

import com.sarada.trading.common.audit.AuditService;
import com.sarada.trading.common.config.AppProperties;
import com.sarada.trading.common.market.SignalType;
import com.sarada.trading.common.market.TradeSignal;
import com.sarada.trading.common.time.TradingClock;
import com.sarada.trading.common.ws.WsPublisher;
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
    private WsPublisher ws;
    private RiskManager riskManager;

    @BeforeEach
    void setUp() {
        stats = Mockito.mock(TradeStatsPort.class);
        TradingClock clock = Mockito.mock(TradingClock.class);
        AuditService audit = Mockito.mock(AuditService.class);
        ws = Mockito.mock(WsPublisher.class);
        AppProperties props = new AppProperties(
                "Asia/Kolkata", "PAPER", null, null,
                new AppProperties.Trading(
                        "NIFTY 50", "NSE", "NFO", 65, 1, 50,
                        LocalTime.of(9, 20), LocalTime.of(15, 5), LocalTime.of(9, 15),
                        5, 4, 2, 0, LocalTime.of(14, 30), new BigDecimal("6500")),
                new AppProperties.Risk(new BigDecimal("25"), new BigDecimal("25"),
                        new BigDecimal("50"), new BigDecimal("30"), new BigDecimal("25")),
                new AppProperties.Strategy(
                        new AppProperties.Strategy.FirstCandleBreakout(9, 15, 14, new BigDecimal("8"), 20,
                                new BigDecimal("20"), 1),
                        null, null, null));
        when(clock.isWithinSession()).thenReturn(true);
        when(clock.tradingDay()).thenReturn(LocalDate.of(2026, 7, 6));
        // default: no extra trades by strategy, profit lock not engaged
        when(stats.tradesOpenedOnByStrategy(Mockito.any(), Mockito.anyString())).thenReturn(0);
        when(stats.profitLockExcessAmount(Mockito.any(), Mockito.any())).thenReturn(null);
        riskManager = new RiskManager(stats, clock, props, audit, ws);
    }

    private TradeSignal signal(String strategyId) {
        return new TradeSignal(strategyId, SignalType.BUY_CE, "NIFTY 50",
                new BigDecimal("24000"), null, "test", Instant.now(), 0, null, null);
    }

    @Test
    void approvesWhenUnderGlobalLimitAndFlat() {
        when(stats.tradesOpenedOn(Mockito.any())).thenReturn(3);
        when(stats.tryReserveSlot("mean-reversion-v1", 2))
                .thenReturn(TradeStatsPort.SlotReservation.granted());
        assertThat(riskManager.evaluateEntry(signal("mean-reversion-v1")).approved()).isTrue();
    }

    @Test
    void rejectsFourthPlusTradeForEveryStrategy() {
        when(stats.tradesOpenedOn(Mockito.any())).thenReturn(4);   // global count = 4 (at limit)
        for (String id : new String[]{"first-candle-breakout-v1", "supertrend-flip-v1",
                "multi-confluence-trend-v1", "mean-reversion-v1"}) {
            RiskManager.Decision d = riskManager.evaluateEntry(signal(id));
            assertThat(d.approved()).as("strategy %s must be blocked at 4/4", id).isFalse();
            assertThat(d.reason()).contains("Daily trade limit reached");
        }
        // Daily limit gate rejects before per-strategy cap or profit lock is ever checked.
        Mockito.verify(stats, Mockito.never()).tryReserveSlot(Mockito.anyString(), Mockito.anyInt());
    }

    @Test
    void rejectsFcbWhenStrategyDailyCapReached() {
        when(stats.tradesOpenedOn(Mockito.any())).thenReturn(1);  // global still has room
        when(stats.tradesOpenedOnByStrategy(Mockito.any(), Mockito.eq("first-candle-breakout-v1"))).thenReturn(1);
        RiskManager.Decision d = riskManager.evaluateEntry(signal("first-candle-breakout-v1"));
        assertThat(d.approved()).isFalse();
        assertThat(d.reason()).contains("ENTRY BLOCKED").contains("first-candle-breakout-v1").contains("daily cap (1)");
        Mockito.verify(stats, Mockito.never()).tryReserveSlot(Mockito.anyString(), Mockito.anyInt());
    }

    @Test
    void doesNotApplyFcbCapToOtherStrategies() {
        // supertrend-flip has no per-strategy cap — even if FCB is exhausted, ST can still trade
        when(stats.tradesOpenedOn(Mockito.any())).thenReturn(1);
        when(stats.tryReserveSlot("supertrend-flip-v1", 2)).thenReturn(TradeStatsPort.SlotReservation.granted());
        RiskManager.Decision d = riskManager.evaluateEntry(signal("supertrend-flip-v1"));
        assertThat(d.approved()).isTrue();
    }

    @Test
    void haltsTradingWhenProfitLockEngaged() {
        when(stats.tradesOpenedOn(Mockito.any())).thenReturn(2);
        when(stats.profitLockExcessAmount(Mockito.any(), Mockito.eq(new BigDecimal("6500"))))
                .thenReturn(new BigDecimal("7000"));
        RiskManager.Decision d = riskManager.evaluateEntry(signal("supertrend-flip-v1"));
        assertThat(d.approved()).isFalse();
        assertThat(d.reason()).contains("TRADING HALTED FOR DAY").contains("7000").contains("6500");
        Mockito.verify(stats, Mockito.never()).tryReserveSlot(Mockito.anyString(), Mockito.anyInt());
    }

    @Test
    void continuesTradingWhenFirstTwoTradesDidNotHitProfitLock() {
        when(stats.tradesOpenedOn(Mockito.any())).thenReturn(2);
        when(stats.profitLockExcessAmount(Mockito.any(), Mockito.any())).thenReturn(null);
        when(stats.tryReserveSlot("supertrend-flip-v1", 2)).thenReturn(TradeStatsPort.SlotReservation.granted());
        RiskManager.Decision d = riskManager.evaluateEntry(signal("supertrend-flip-v1"));
        assertThat(d.approved()).isTrue();
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
