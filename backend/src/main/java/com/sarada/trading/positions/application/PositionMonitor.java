package com.sarada.trading.positions.application;

import com.sarada.trading.common.events.DomainEvents;
import com.sarada.trading.common.market.Tick;
import com.sarada.trading.positions.domain.PositionEntity;
import com.sarada.trading.positions.infra.PositionRepository;
import com.sarada.trading.risk.domain.TrailingStopPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Watches every tick of the open position's option contract:
 *   1. advances the trailing stop (T1 → breakeven, T2 → entry+30, then +25 steps)
 *   2. exits the moment the stop is touched
 *   3. streams the live position + P&L to the dashboard (throttled)
 *
 * Stop/target evaluation is tick-level, not candle-level — exits fire within
 * one tick of the level being touched.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PositionMonitor {

    private final PositionRepository positions;
    private final PositionService positionService;
    private final TrailingStopPolicy stopPolicy;

    private final Map<Long, Long> lastPushAt = new ConcurrentHashMap<>();

    @EventListener
    public void onTick(DomainEvents.TickReceived event) {
        Tick tick = event.tick();
        positions.findFirstByStatusOrderByOpenedAtDesc(PositionEntity.Status.OPEN)
                .filter(p -> p.getInstrumentToken() == tick.instrumentToken())
                .ifPresent(position -> manage(position, tick.lastPrice()));
    }

    private void manage(PositionEntity position, BigDecimal price) {
        // 1. trail the stop
        TrailingStopPolicy.StopState advanced = stopPolicy.advance(
                position.getEntryPrice(), price, position.getStopLoss(), position.getRiskStage());
        if (advanced.stopLoss().compareTo(position.getStopLoss()) > 0) {
            position.updateStop(advanced.stopLoss(), advanced.stage());
            positions.save(position);
            log.info("Stop advanced [{}] {} → {} ({})", position.getTradingsymbol(),
                    position.getEntryPrice(), advanced.stopLoss(), advanced.stage());
        }

        // 2. stop hit?
        if (stopPolicy.isStopHit(price, position.getStopLoss())) {
            String reason = switch (position.getRiskStage()) {
                case INITIAL -> "STOP_LOSS";
                case BREAKEVEN -> "BREAKEVEN_STOP";
                case LOCKED, TRAILING -> "TRAIL_STOP";
            };
            try {
                positionService.exit(position.getId(), reason, "SYSTEM");
            } catch (Exception e) {
                log.error("Auto-exit failed for {}: {}", position.getId(), e.getMessage());
            }
            return;
        }

        // 3. live stream (max 2/sec per position)
        long now = System.currentTimeMillis();
        Long last = lastPushAt.get(position.getId());
        if (last == null || now - last >= 500) {
            lastPushAt.put(position.getId(), now);
            positionService.publishPosition(position, price);
            positionService.publishPnl();
        }
    }
}
