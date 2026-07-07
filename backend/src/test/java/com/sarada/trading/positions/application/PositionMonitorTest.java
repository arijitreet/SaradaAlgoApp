package com.sarada.trading.positions.application;

import com.sarada.trading.common.events.DomainEvents;
import com.sarada.trading.common.market.Tick;
import com.sarada.trading.positions.domain.PositionEntity;
import com.sarada.trading.positions.domain.UnderlyingPricePort;
import com.sarada.trading.positions.infra.PositionRepository;
import com.sarada.trading.risk.domain.TrailingStopPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * With up to 2 concurrent OPEN positions, every tick must be evaluated against
 * ALL open positions matching that instrument token — not just the most recently
 * opened one. (The old single-trade implementation used findFirst...OrderByOpenedAtDesc,
 * which silently stopped monitoring every position except the newest.)
 */
class PositionMonitorTest {

    private static final long TOKEN_A = 111L;
    private static final long TOKEN_B = 222L;

    private PositionRepository positions;
    private PositionService positionService;
    private TrailingStopPolicy stopPolicy;
    private PositionMonitor monitor;

    private PositionEntity older; // opened first, on TOKEN_A
    private PositionEntity newer; // opened second, on TOKEN_B

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        positions = Mockito.mock(PositionRepository.class);
        positionService = Mockito.mock(PositionService.class);
        stopPolicy = Mockito.mock(TrailingStopPolicy.class);
        UnderlyingPricePort underlyingPrice = Mockito.mock(UnderlyingPricePort.class);

        older = PositionEntity.open(LocalDate.now(), "supertrend-flip-v1", null, 1L,
                TOKEN_A, "NIFTY25JUL24400CE", "CE", new BigDecimal("24400"), LocalDate.now(),
                65, new BigDecimal("100"),
                new BigDecimal("75"), new BigDecimal("125"), new BigDecimal("150"), null, null);
        newer = PositionEntity.open(LocalDate.now(), "first-candle-breakout-v1", null, 2L,
                TOKEN_B, "NIFTY25JUL24500PE", "PE", new BigDecimal("24500"), LocalDate.now(),
                65, new BigDecimal("100"),
                new BigDecimal("75"), new BigDecimal("125"), new BigDecimal("150"), null, null);
        setId(older, 501L);
        setId(newer, 502L);

        // findByStatus returns both, older first — mirrors real repo ordering by id/opened time.
        when(positions.findByStatus(PositionEntity.Status.OPEN)).thenReturn(List.of(older, newer));

        monitor = new PositionMonitor(positions, positionService, stopPolicy, underlyingPrice);
    }

    private static void setId(PositionEntity entity, long id) throws ReflectiveOperationException {
        var idField = PositionEntity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }

    @Test
    void tickForTheOlderPositionsTokenStillTriggersItsStopCheck() {
        // Under the old findFirst(...)-based implementation this tick would resolve to
        // `newer` (most recently opened), fail the token filter, and silently do nothing —
        // `older` would never have its stop evaluated again.
        when(stopPolicy.advance(any(), any(), any(), any(), eq("supertrend-flip-v1")))
                .thenReturn(new TrailingStopPolicy.StopState(new BigDecimal("75"), TrailingStopPolicy.Stage.INITIAL));
        when(stopPolicy.isStopHit(new BigDecimal("70"), new BigDecimal("75"))).thenReturn(true);

        Tick tick = new Tick(TOKEN_A, new BigDecimal("70"), 0, Instant.now());
        monitor.onTick(new DomainEvents.TickReceived(tick));

        verify(positionService).exit(eq(501L), eq("STOP_LOSS"), eq("SYSTEM"));
        verify(positionService, never()).exit(eq(502L), any(), any());
    }

    @Test
    void tickForTheNewerPositionsTokenOnlyManagesThatPosition() {
        when(stopPolicy.advance(any(), any(), any(), any(), eq("first-candle-breakout-v1")))
                .thenReturn(new TrailingStopPolicy.StopState(new BigDecimal("75"), TrailingStopPolicy.Stage.INITIAL));
        when(stopPolicy.isStopHit(new BigDecimal("70"), new BigDecimal("75"))).thenReturn(true);

        Tick tick = new Tick(TOKEN_B, new BigDecimal("70"), 0, Instant.now());
        monitor.onTick(new DomainEvents.TickReceived(tick));

        verify(positionService).exit(eq(502L), eq("STOP_LOSS"), eq("SYSTEM"));
        verify(positionService, never()).exit(eq(501L), any(), any());
    }

    @Test
    void tickMatchingNeitherTokenManagesNothing() {
        Tick tick = new Tick(999L, new BigDecimal("70"), 0, Instant.now());
        monitor.onTick(new DomainEvents.TickReceived(tick));

        verify(positionService, never()).exit(anyLong(), any(), any());
    }
}
