package com.sarada.trading.positions.application;

import com.sarada.trading.broker.application.FeedSubscriptions;
import com.sarada.trading.common.audit.AuditService;
import com.sarada.trading.common.config.AppProperties;
import com.sarada.trading.common.events.DomainEvents;
import com.sarada.trading.common.market.LivePriceCache;
import com.sarada.trading.common.ws.WsPublisher;
import com.sarada.trading.positions.domain.BrokerPositionsPort;
import com.sarada.trading.positions.domain.PositionEntity;
import com.sarada.trading.positions.infra.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Simulates two scenarios:
 *
 * 1. RESTART — OPEN position exists in DB but all in-memory state is gone.
 *    Recovery must resubscribe the feed and republish — never place orders.
 *
 * 2. GHOST TRADE — position is OPEN in DB but user manually closed it on Kite.
 *    Reconciliation must call closeExternal() (no order) and never call exit().
 */
class PositionRecoveryServiceTest {

    private static final long TOKEN = 14430722L;

    private PositionRepository positions;
    private PositionService positionService;
    private FeedSubscriptions feedSubscriptions;
    private BrokerPositionsPort brokerPositions;
    private AuditService audit;
    private WsPublisher ws;
    private PositionRecoveryService recovery;
    private PositionEntity open;

    @BeforeEach
    void setUp() {
        positions = Mockito.mock(PositionRepository.class);
        positionService = Mockito.mock(PositionService.class);
        feedSubscriptions = new FeedSubscriptions();          // real: verifies registry effect
        brokerPositions = Mockito.mock(BrokerPositionsPort.class);
        audit = Mockito.mock(AuditService.class);
        ws = Mockito.mock(WsPublisher.class);
        LivePriceCache priceCache = Mockito.mock(LivePriceCache.class);
        when(priceCache.ltp(TOKEN)).thenReturn(Optional.empty());

        AppProperties appProps = Mockito.mock(AppProperties.class);
        when(appProps.zone()).thenReturn(ZoneId.of("Asia/Kolkata"));

        // The position exactly as persisted at entry time — original SL, never recalculated.
        open = PositionEntity.open(LocalDate.now(), "supertrend-flip-v1", null, 1L,
                TOKEN, "NIFTY25JUL24400CE", "CE", new BigDecimal("24400"), LocalDate.now(),
                65, new BigDecimal("146.60"),
                new BigDecimal("131.60"), new BigDecimal("161.60"), new BigDecimal("176.60"),
                null, null);
        when(positions.findByStatus(PositionEntity.Status.OPEN)).thenReturn(List.of(open));

        // Assign a synthetic DB id — the entity is not persisted in this unit test,
        // so getId() would return null, causing a NullPointerException when
        // reconcileWithBroker() tries to unbox it to long.
        try {
            var idField = PositionEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(open, 99L);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }

        recovery = new PositionRecoveryService(positions, positionService, feedSubscriptions,
                brokerPositions, priceCache, audit, ws, appProps);
    }

    @Test
    void rehydrationResubscribesFeedAndRepublishesWithPersistedValues() {
        recovery.rehydrateOpenPositions();

        // Feed registry now holds the option token → ticker resubscribes on connect
        // → PositionMonitor resumes with the persisted entry/SL (146.60 / 131.60).
        assertThat(feedSubscriptions.all()).contains(TOKEN);
        verify(positionService).publishPosition(eq(open), eq(new BigDecimal("146.60")));
        verify(audit).log(eq("POSITION"), eq("RECOVERED"), anyString());
        // Rehydration must never trigger an exit or any order.
        verify(positionService, never()).exit(anyLong(), anyString(), anyString());
    }

    @Test
    void reconciliationClosesLocallyWhenBrokerShowsZero() {
        // Broker book does NOT contain the locally-open symbol → manual exit detected.
        when(brokerPositions.openNetQuantities()).thenReturn(Optional.of(Map.of()));

        recovery.onBrokerConnected(new DomainEvents.BrokerConnectionChanged("CONNECTED", "up"));

        // Must audit the mismatch, surface it in the UI feed, and close the DB record
        // without placing any broker order.
        verify(audit).log(eq("POSITION"), eq("RECOVERY_MISMATCH"), contains("NIFTY25JUL24400CE"));
        verify(ws).feed(eq("RISK"), anyString(), contains("NIFTY25JUL24400CE"));
        verify(positionService).closeExternal(99L);
        verify(positionService, never()).exit(anyLong(), anyString(), anyString());
    }

    @Test
    void reconciliationPassesQuietlyWhenBrokerMatches() {
        when(brokerPositions.openNetQuantities())
                .thenReturn(Optional.of(Map.of("NIFTY25JUL24400CE", 65)));

        recovery.onBrokerConnected(new DomainEvents.BrokerConnectionChanged("CONNECTED", "up"));

        verify(audit, never()).log(eq("POSITION"), eq("RECOVERY_MISMATCH"), anyString());
        verify(positionService, never()).closeExternal(99L);
        verify(positionService, never()).exit(anyLong(), anyString(), anyString());
    }

    @Test
    void reconciliationActsWhenBrokerBecomesAvailable() {
        // First connect: broker unavailable → skip silently (no reconciled flag, just retries).
        when(brokerPositions.openNetQuantities()).thenReturn(Optional.empty());
        recovery.onBrokerConnected(new DomainEvents.BrokerConnectionChanged("CONNECTED", "up"));
        verify(positionService, never()).closeExternal(99L);

        // Second connect: book available and shows mismatch → closes locally.
        when(brokerPositions.openNetQuantities()).thenReturn(Optional.of(Map.of()));
        recovery.onBrokerConnected(new DomainEvents.BrokerConnectionChanged("CONNECTED", "up"));
        verify(positionService).closeExternal(99L);
        verify(positionService, never()).exit(anyLong(), anyString(), anyString());
    }
}
