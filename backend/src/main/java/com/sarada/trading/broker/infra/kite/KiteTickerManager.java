package com.sarada.trading.broker.infra.kite;

import com.sarada.trading.broker.application.FeedSubscriptions;
import com.sarada.trading.common.config.AppProperties;
import com.sarada.trading.common.events.DomainEvents;
import com.sarada.trading.common.market.Tick;
import com.zerodhatech.ticker.KiteTicker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;

/**
 * Manages the Kite WebSocket market feed: connect, subscribe, and automatic
 * reconnection (SDK-level retries + resubscription of all tracked tokens on
 * every reconnect). Connection state changes are broadcast as domain events.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KiteTickerManager {

    public enum State { DISCONNECTED, CONNECTING, CONNECTED }

    private final AppProperties props;
    private final FeedSubscriptions subscriptions;
    private final ApplicationEventPublisher events;

    private volatile KiteTicker ticker;
    private volatile State state = State.DISCONNECTED;

    /** Forward new subscriptions to the live ticker (registered once). */
    @jakarta.annotation.PostConstruct
    void wireSubscriptionForwarding() {
        subscriptions.onSubscribe(token -> {
            if (isConnected()) {
                subscribeTokens(new ArrayList<>(java.util.List.of(token)));
            }
        });
    }

    public State state() {
        return state;
    }

    public boolean isConnected() {
        return state == State.CONNECTED;
    }

    public synchronized void connect(String accessToken) {
        disconnect();
        state = State.CONNECTING;
        publishState("RECONNECTING", "Connecting to Kite ticker");

        KiteTicker newTicker = new KiteTicker(accessToken, props.kite().apiKey());
        newTicker.setTryReconnection(true);
        try {
            newTicker.setMaximumRetries(50);
            newTicker.setMaximumRetryInterval(60);
        } catch (Exception | com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException e) {
            // KiteException extends Throwable (SDK quirk), so it needs its own clause
            log.warn("Could not tune ticker retry policy: {}", e.getMessage());
        }

        newTicker.setOnConnectedListener(() -> {
            state = State.CONNECTED;
            log.info("Kite ticker connected");
            publishState("CONNECTED", "Market feed live");
            resubscribeAll();
        });

        newTicker.setOnDisconnectedListener(() -> {
            state = State.DISCONNECTED;
            log.warn("Kite ticker disconnected");
            publishState("DISCONNECTED", "Market feed lost; SDK retrying");
        });

        newTicker.setOnTickerArrivalListener(ticks -> {
            for (com.zerodhatech.models.Tick kiteTick : ticks) {
                Instant ts = kiteTick.getTickTimestamp() != null
                        ? kiteTick.getTickTimestamp().toInstant()
                        : Instant.now();
                events.publishEvent(new DomainEvents.TickReceived(new Tick(
                        kiteTick.getInstrumentToken(),
                        BigDecimal.valueOf(kiteTick.getLastTradedPrice()),
                        (long) kiteTick.getVolumeTradedToday(),
                        ts)));
            }
        });

        this.ticker = newTicker;
        newTicker.connect();
    }

    private void resubscribeAll() {
        ArrayList<Long> tokens = new ArrayList<>(subscriptions.all());
        if (!tokens.isEmpty()) {
            subscribeTokens(tokens);
        }
    }

    private void subscribeTokens(ArrayList<Long> tokens) {
        try {
            ticker.subscribe(tokens);
            ticker.setMode(tokens, KiteTicker.modeFull);
            log.info("Subscribed to {} instrument(s)", tokens.size());
        } catch (Exception e) {
            log.error("Ticker subscribe failed: {}", e.getMessage());
        }
    }

    public synchronized void disconnect() {
        if (ticker != null) {
            try {
                ticker.disconnect();
            } catch (Exception ignored) {
            }
            ticker = null;
        }
        state = State.DISCONNECTED;
    }

    private void publishState(String wireState, String detail) {
        events.publishEvent(new DomainEvents.BrokerConnectionChanged(wireState, detail));
    }
}
