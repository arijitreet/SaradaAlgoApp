package com.sarada.trading.broker.application;

import com.sarada.trading.broker.domain.BrokerSession;
import com.sarada.trading.broker.infra.BrokerSessionRepository;
import com.sarada.trading.broker.infra.kite.KiteClientFactory;
import com.sarada.trading.broker.infra.kite.KiteTickerManager;
import com.sarada.trading.common.audit.AuditService;
import com.sarada.trading.common.config.AppProperties;
import com.sarada.trading.common.crypto.TokenEncryptor;
import com.sarada.trading.common.error.DomainException;
import com.sarada.trading.common.events.DomainEvents;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.models.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Zerodha OAuth lifecycle: build the login URL, exchange the request token,
 * persist the (encrypted) access token, and bring the market feed online.
 * Kite tokens expire daily — expiry is pinned to 07:00 IST next day.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BrokerAuthService {

    private final AppProperties props;
    private final BrokerSessionRepository sessions;
    private final TokenEncryptor encryptor;
    private final KiteClientFactory clientFactory;
    private final KiteTickerManager tickerManager;
    private final ApplicationEventPublisher events;
    private final AuditService audit;

    public String loginUrl() {
        if (props.kite().apiKey() == null || props.kite().apiKey().isBlank()) {
            throw new DomainException("KITE_API_KEY is not configured");
        }
        return clientFactory.unauthenticated().getLoginURL();
    }

    @Transactional
    public void completeLogin(String requestToken) {
        try {
            KiteConnect kite = clientFactory.unauthenticated();
            User kiteUser = kite.generateSession(requestToken, props.kite().apiSecret());

            sessions.findFirstByActiveTrueOrderByLoginTimeDesc().ifPresent(old -> {
                old.setActive(false);
                sessions.save(old);
            });

            BrokerSession session = new BrokerSession();
            session.setKiteUserId(kiteUser.userId);
            session.setAccessTokenEncrypted(encryptor.encrypt(kiteUser.accessToken));
            if (kiteUser.publicToken != null) {
                session.setPublicTokenEncrypted(encryptor.encrypt(kiteUser.publicToken));
            }
            session.setLoginTime(Instant.now());
            session.setExpiresAt(nextDailyExpiry());
            session.setActive(true);
            sessions.save(session);

            audit.log("BROKER", "KITE_LOGIN", "Kite session established for " + kiteUser.userId);
            events.publishEvent(new DomainEvents.BrokerConnectionChanged(
                    "AUTHENTICATED", "Kite login complete"));

            tickerManager.connect(kiteUser.accessToken);
        } catch (DomainException e) {
            throw e;
        } catch (com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException ke) {
            // KiteException never calls super(message), so getMessage() returns null.
            // Read the public `message` field directly.
            String detail = ke.getClass().getSimpleName() + " [" + ke.code + "]: " + ke.message;
            audit.log("BROKER", "KITE_LOGIN_FAILED", detail);
            throw new DomainException("Kite login failed: " + detail);
        } catch (Exception e) {
            audit.log("BROKER", "KITE_LOGIN_FAILED", e.getMessage());
            throw new DomainException("Kite login failed: " + e.getMessage());
        }
    }

    /** Reconnect the feed on boot if a still-valid session exists. */
    @EventListener(ApplicationReadyEvent.class)
    public void reconnectOnBoot() {
        clientFactory.activeAccessToken().ifPresentOrElse(
                token -> {
                    log.info("Active Kite session found — connecting market feed");
                    tickerManager.connect(token);
                },
                () -> {
                    log.warn("No active Kite session — orders will be rejected until OAuth login is completed");
                    events.publishEvent(new DomainEvents.BrokerConnectionChanged(
                            "UNAUTHENTICATED", "Kite session expired or missing — please login via Settings"));
                });
    }

    public boolean isAuthenticated() {
        return clientFactory.activeAccessToken().isPresent();
    }

    public record BrokerStatus(boolean authenticated, String kiteUserId, Instant expiresAt,
                               String feedState, String mode) {}

    public BrokerStatus status() {
        return sessions.findFirstByActiveTrueOrderByLoginTimeDesc()
                .filter(s -> !s.isExpired())
                .map(s -> new BrokerStatus(true, s.getKiteUserId(), s.getExpiresAt(),
                        tickerManager.state().name(), props.brokerMode()))
                .orElseGet(() -> new BrokerStatus(false, null, null,
                        tickerManager.state().name(), props.brokerMode()));
    }

    private Instant nextDailyExpiry() {
        return LocalDate.now(props.zone()).plusDays(1)
                .atTime(LocalTime.of(7, 0))
                .atZone(props.zone())
                .toInstant();
    }
}
