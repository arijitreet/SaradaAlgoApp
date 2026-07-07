package com.sarada.trading.positions.application;

import com.sarada.trading.common.audit.AuditService;
import com.sarada.trading.common.market.LivePriceCache;
import com.sarada.trading.common.time.TradingClock;
import com.sarada.trading.common.ws.WsPublisher;
import com.sarada.trading.orders.application.OrderService;
import com.sarada.trading.positions.domain.BrokerPositionsPort;
import com.sarada.trading.positions.domain.PositionEntity;
import com.sarada.trading.positions.infra.PositionRepository;
import com.sarada.trading.risk.domain.TradeStatsPort;
import com.sarada.trading.risk.domain.TrailingStopPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Concurrent-trade slot reservation: at most maxConcurrentTrades positions may be
 * OPEN (or reserved for an in-flight entry) at once, AND no single strategy may hold
 * more than one of those slots at a time (bug: a re-breakout of the same strategy's
 * signal was consuming the 2nd slot while its first trade was still active). Both
 * checks happen atomically inside tryReserveSlot so two signals evaluated around the
 * same instant can never both take the last slot, or both slip past the same-strategy
 * check (see RiskManager.evaluateEntry, which calls tryReserveSlot).
 */
class PositionServiceTest {

    private PositionRepository positions;
    private PositionService service;

    private static final String FCB = "first-candle-breakout-v1";
    private static final String ST = "supertrend-flip-v1";

    @BeforeEach
    void setUp() {
        positions = Mockito.mock(PositionRepository.class);
        OrderService orderService = Mockito.mock(OrderService.class);
        BrokerPositionsPort brokerPositions = Mockito.mock(BrokerPositionsPort.class);
        TrailingStopPolicy stopPolicy = Mockito.mock(TrailingStopPolicy.class);
        LivePriceCache priceCache = Mockito.mock(LivePriceCache.class);
        TradingClock clock = Mockito.mock(TradingClock.class);
        AuditService audit = Mockito.mock(AuditService.class);
        WsPublisher ws = Mockito.mock(WsPublisher.class);
        ApplicationEventPublisher events = Mockito.mock(ApplicationEventPublisher.class);

        service = new PositionService(positions, orderService, brokerPositions, stopPolicy,
                priceCache, clock, audit, ws, events);
        when(positions.findByStatus(PositionEntity.Status.OPEN)).thenReturn(List.of());
        service.initActiveSlots();
    }

    @Test
    void reservesSlotsUpToConfiguredMaxThenRejectsTheThird() {
        assertThat(service.tryReserveSlot(FCB, 2).approved()).isTrue();   // 0 -> 1
        assertThat(service.tryReserveSlot(ST, 2).approved()).isTrue();    // 1 -> 2 (different strategy)
        assertThat(service.tryReserveSlot("mean-reversion-v1", 2).approved()).isFalse();  // at cap
    }

    @Test
    void releasingASlotFreesItForReuse() {
        assertThat(service.tryReserveSlot(FCB, 2).approved()).isTrue();
        assertThat(service.tryReserveSlot(ST, 2).approved()).isTrue();
        assertThat(service.tryReserveSlot("mean-reversion-v1", 2).approved()).isFalse();

        service.releaseSlot(FCB);   // FCB's trade exits
        assertThat(service.tryReserveSlot("mean-reversion-v1", 2).approved()).isTrue();   // freed slot reused
    }

    @Test
    void sameStrategyReEntryIsBlockedWhileItsTradeIsStillActive() {
        // First entry for FCB reserves a slot (in-flight; no id yet).
        assertThat(service.tryReserveSlot(FCB, 2).approved()).isTrue();

        // Re-breakout of the SAME level fires another FCB signal — must be blocked even
        // though a second global slot is still free (that slot is for OTHER strategies).
        TradeStatsPort.SlotReservation second = service.tryReserveSlot(FCB, 2);
        assertThat(second.approved()).isFalse();
        assertThat(second.blockedBySameStrategy()).isTrue();
        assertThat(second.activeTradeId()).isNull();   // entry still in flight, no position id yet
    }

    @Test
    void differentStrategyIsAllowedWhileFirstStrategyActive() {
        assertThat(service.tryReserveSlot(FCB, 2).approved()).isTrue();

        TradeStatsPort.SlotReservation viaOtherStrategy = service.tryReserveSlot(ST, 2);
        assertThat(viaOtherStrategy.approved()).isTrue();
    }

    @Test
    void sameStrategyMayReEnterAfterItsTradeCloses() {
        assertThat(service.tryReserveSlot(FCB, 2).approved()).isTrue();
        assertThat(service.tryReserveSlot(FCB, 2).approved()).isFalse();

        service.releaseSlot(FCB);   // trade exits

        assertThat(service.tryReserveSlot(FCB, 2).approved()).isTrue();   // fresh signal, allowed again
    }

    @Test
    void startupSeedsActiveSlotsAndStrategyOwnershipFromExistingOpenPositions() {
        // Restart recovery scenario: FCB already has an OPEN position in the DB before the
        // JVM restarted — the in-memory guard must know about it immediately, not start empty.
        PositionEntity open = PositionEntity.open(LocalDate.now(), FCB, null, 1L,
                111L, "NIFTY25JUL24400CE", "CE", new BigDecimal("24400"), LocalDate.now(),
                65, new BigDecimal("100"), new BigDecimal("85"), new BigDecimal("125"),
                new BigDecimal("150"), null, null);
        setId(open, 77L);
        when(positions.findByStatus(PositionEntity.Status.OPEN)).thenReturn(List.of(open));

        service.initActiveSlots();

        // Same strategy blocked, with the real rehydrated trade id in the rejection.
        TradeStatsPort.SlotReservation blocked = service.tryReserveSlot(FCB, 2);
        assertThat(blocked.approved()).isFalse();
        assertThat(blocked.blockedBySameStrategy()).isTrue();
        assertThat(blocked.activeTradeId()).isEqualTo(77L);

        // A different strategy can still take the second slot.
        assertThat(service.tryReserveSlot(ST, 2).approved()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "first-candle-breakout-v1",      // EMA9/15 crossover filter lives here
            "supertrend-flip-v1",
            "multi-confluence-trend-v1",     // "trend following"
            "mean-reversion-v1"
    })
    void guardAppliesUniformlyToEveryRegisteredStrategy(String strategyId) {
        // The gate is keyed purely on the strategy-id string with zero special-casing,
        // so this must hold identically for all four real strategies.
        assertThat(service.tryReserveSlot(strategyId, 2).approved()).isTrue();

        TradeStatsPort.SlotReservation reFire = service.tryReserveSlot(strategyId, 2);
        assertThat(reFire.approved()).isFalse();
        assertThat(reFire.blockedBySameStrategy()).isTrue();

        service.releaseSlot(strategyId);
        assertThat(service.tryReserveSlot(strategyId, 2).approved()).isTrue();
    }

    @Test
    void releaseIsANoOpWhenStrategyHoldsNoSlot() {
        service.releaseSlot("nonexistent-strategy");   // must not throw or corrupt the count
        assertThat(service.tryReserveSlot(FCB, 1).approved()).isTrue();
        assertThat(service.tryReserveSlot(ST, 1).approved()).isFalse();
    }

    private static void setId(PositionEntity entity, long id) {
        try {
            var idField = PositionEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
