package com.sarada.trading.broker.application;

import com.sarada.trading.broker.infra.kite.KiteClientFactory;
import com.sarada.trading.broker.infra.kite.KiteTickerManager;
import com.sarada.trading.common.audit.AuditService;
import com.sarada.trading.common.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/**
 * Watchdog on top of the SDK's own retry loop: if the feed is authenticated
 * but down during market hours, force a fresh connect cycle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionMonitor {

    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 0);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 35);

    private final KiteTickerManager tickerManager;
    private final KiteClientFactory clientFactory;
    private final AppProperties props;
    private final AuditService audit;

    @Scheduled(fixedDelay = 30_000, initialDelay = 60_000)
    public void watchdog() {
        if (!isMarketHours() || tickerManager.isConnected()) {
            return;
        }
        clientFactory.activeAccessToken().ifPresent(token -> {
            log.warn("Feed down during market hours — forcing reconnect");
            audit.log("BROKER", "FEED_RECONNECT", "Watchdog forced ticker reconnect");
            tickerManager.connect(token);
        });
    }

    private boolean isMarketHours() {
        ZonedDateTime now = ZonedDateTime.now(props.zone());
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        return !time.isBefore(MARKET_OPEN) && !time.isAfter(MARKET_CLOSE);
    }
}
