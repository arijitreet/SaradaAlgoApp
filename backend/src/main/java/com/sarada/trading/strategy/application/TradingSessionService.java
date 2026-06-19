package com.sarada.trading.strategy.application;

import com.sarada.trading.common.audit.AuditService;
import com.sarada.trading.common.config.AppProperties;
import com.sarada.trading.common.error.DomainException;
import com.sarada.trading.common.events.DomainEvents;
import com.sarada.trading.common.settings.AppSettingEntity;
import com.sarada.trading.common.settings.AppSettingRepository;
import com.sarada.trading.common.time.TradingClock;
import com.sarada.trading.common.ws.WsPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Manual start/stop of the trading engine. The running day is persisted in
 * {@code app_settings} so a restart mid-session resumes exactly where it left
 * off — stale values from a previous day are ignored automatically.
 * Auto-stops at session end.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradingSessionService {

    private static final String KEY = "trading:session:running-day";

    private final AppSettingRepository settings;
    private final TradingClock clock;
    private final AppProperties props;
    private final ApplicationEventPublisher events;
    private final AuditService audit;
    private final WsPublisher ws;

    @Transactional(readOnly = true)
    public boolean isRunning() {
        return settings.findById(KEY)
                .map(AppSettingEntity::getValue)
                .filter(day -> clock.tradingDay().toString().equals(day))
                .isPresent();
    }

    @Transactional
    public void start(String actor) {
        if (clock.isAfterSessionEnd()) {
            throw new DomainException("Session window (" + props.trading().sessionStart()
                    + "–" + props.trading().sessionEnd() + " IST) is over for today");
        }
        settings.save(new AppSettingEntity(KEY, clock.tradingDay().toString(), Instant.now()));
        audit.log("SESSION", "START", "Trading session started", actor);
        broadcast(true, "Started by " + actor);
        log.info("Trading session STARTED by {}", actor);
    }

    @Transactional
    public void stop(String actor, String reason) {
        if (settings.existsById(KEY)) {
            settings.deleteById(KEY);
        }
        audit.log("SESSION", "STOP", reason, actor);
        broadcast(false, reason);
        log.info("Trading session STOPPED by {} ({})", actor, reason);
    }

    /** Auto-stop new entries right after the force-exit cutoff (15:05 IST). */
    @Scheduled(cron = "30 5 15 * * MON-FRI", zone = "Asia/Kolkata")
    public void autoStopAtSessionEnd() {
        if (isRunning()) {
            stop("SYSTEM", "Auto-stop at session end " + props.trading().sessionEnd());
        }
    }

    private void broadcast(boolean running, String reason) {
        events.publishEvent(new DomainEvents.SessionStateChanged(running, reason));
        ws.publish(WsPublisher.TOPIC_SESSION, new SessionStatus(running, reason, Instant.now()));
        ws.feed("SESSION", running ? "Session started" : "Session stopped", reason);
    }

    public record SessionStatus(boolean running, String reason, Instant at) {}

    public SessionStatus status() {
        return new SessionStatus(isRunning(), null, Instant.now());
    }
}
