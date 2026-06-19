package com.sarada.trading.marketdata.application;

import com.sarada.trading.broker.infra.kite.KiteClientFactory;
import com.sarada.trading.common.audit.AuditService;
import com.sarada.trading.common.config.AppProperties;
import com.sarada.trading.common.events.DomainEvents;
import com.sarada.trading.marketdata.domain.InstrumentEntity;
import com.sarada.trading.marketdata.infra.InstrumentRepository;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.models.Instrument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Maintains the NIFTY option universe: downloads the NFO instrument dump after
 * Kite login (and daily at 08:45 IST), and resolves the underlying index token.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstrumentService {

    private static final long NIFTY_INDEX_TOKEN_FALLBACK = 256265L; // NSE:NIFTY 50
    private static final String OPTION_NAME = "NIFTY";

    private final KiteClientFactory kiteClientFactory;
    private final InstrumentRepository instruments;
    private final AppProperties props;
    private final AuditService audit;

    public long underlyingToken() {
        return kiteClientFactory.authenticated()
                .map(kite -> {
                    try {
                        String key = props.trading().underlyingExchange() + ":" + props.trading().underlying();
                        return kite.getLTP(new String[]{key}).get(key).instrumentToken;
                    } catch (Exception | com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException e) {
                        log.warn("Underlying token lookup failed, using fallback: {}", e.getMessage());
                        return NIFTY_INDEX_TOKEN_FALLBACK;
                    }
                })
                .orElse(NIFTY_INDEX_TOKEN_FALLBACK);
    }

    @EventListener
    @Async("appExecutor")
    public void onBrokerAuthenticated(DomainEvents.BrokerConnectionChanged event) {
        if ("AUTHENTICATED".equals(event.state())) {
            refreshOptionUniverse();
        }
    }

    /** Daily pre-market refresh keeps expiries current (rollover safety net). */
    @Scheduled(cron = "0 45 8 * * MON-FRI", zone = "Asia/Kolkata")
    public void scheduledRefresh() {
        refreshOptionUniverse();
    }

    public synchronized void refreshOptionUniverse() {
        kiteClientFactory.authenticated().ifPresent(this::refreshFrom);
    }

    private void refreshFrom(KiteConnect kite) {
        try {
            List<Instrument> dump = kite.getInstruments(props.trading().optionExchange());
            ZoneId zone = props.zone();
            List<InstrumentEntity> options = dump.stream()
                    .filter(i -> OPTION_NAME.equals(i.name))
                    .filter(i -> "CE".equals(i.instrument_type) || "PE".equals(i.instrument_type))
                    .filter(i -> i.expiry != null && i.strike != null)
                    .map(i -> InstrumentEntity.builder()
                            .instrumentToken(i.instrument_token)
                            .tradingsymbol(i.tradingsymbol)
                            .name(i.name)
                            .exchange(i.exchange)
                            .segment(i.segment)
                            .instrumentType(i.instrument_type)
                            .strike(new BigDecimal(i.strike))
                            .expiry(i.expiry.toInstant().atZone(zone).toLocalDate())
                            .lotSize(i.lot_size)
                            .tickSize(BigDecimal.valueOf(i.tick_size))
                            .refreshedAt(Instant.now())
                            .build())
                    .toList();
            instruments.saveAll(options);
            audit.log("SYSTEM", "INSTRUMENTS_REFRESHED", options.size() + " NIFTY option contracts loaded");
            log.info("Instrument universe refreshed: {} NIFTY options", options.size());
        } catch (Exception | com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException e) {
            log.error("Instrument refresh failed: {}", e.getMessage());
            audit.log("SYSTEM", "INSTRUMENTS_REFRESH_FAILED", e.getMessage());
        }
    }

    /**
     * Nearest expiry on/after {@code from}, preferring Thursday (primary Nifty weekly).
     * Falls back to the absolute nearest if no Thursday is found in the first 10 candidates.
     * Returns null when the universe is empty (demo/paper mode without a Kite dump).
     */
    public LocalDate nearestExpiry(LocalDate from) {
        List<LocalDate> candidates = instruments.findUpcomingExpiries(
                OPTION_NAME, from, PageRequest.of(0, 10));
        if (candidates.isEmpty()) return null;

        return candidates.stream()
                .filter(d -> d.getDayOfWeek() == DayOfWeek.TUESDAY)
                .findFirst()
                .orElseGet(() -> {
                    LocalDate fallback = candidates.get(0);
                    log.warn("No Tuesday expiry found on/after {}; using {} ({})",
                            from, fallback, fallback.getDayOfWeek());
                    return fallback;
                });
    }
}
