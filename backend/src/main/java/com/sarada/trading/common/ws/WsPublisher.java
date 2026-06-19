package com.sarada.trading.common.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Single egress point for real-time pushes to the dashboard.
 * Topic catalogue is documented in docs/WEBSOCKET.md.
 */
@Component
@RequiredArgsConstructor
public class WsPublisher {

    public static final String TOPIC_MARKET = "/topic/market";
    public static final String TOPIC_INDICATORS = "/topic/indicators";
    public static final String TOPIC_SIGNALS = "/topic/signals";
    public static final String TOPIC_ORDERS = "/topic/orders";
    public static final String TOPIC_POSITION = "/topic/position";
    public static final String TOPIC_PNL = "/topic/pnl";
    public static final String TOPIC_SESSION = "/topic/session";
    public static final String TOPIC_CONNECTION = "/topic/connection";
    public static final String TOPIC_FEED = "/topic/feed";

    private final SimpMessagingTemplate template;

    public void publish(String topic, Object payload) {
        template.convertAndSend(topic, payload);
    }

    /** Activity-feed convenience: small typed entries the UI renders as a timeline. */
    public void feed(String kind, String title, String detail) {
        publish(TOPIC_FEED, new FeedEntry(kind, title, detail, java.time.Instant.now()));
    }

    public record FeedEntry(String kind, String title, String detail, java.time.Instant at) {}
}
