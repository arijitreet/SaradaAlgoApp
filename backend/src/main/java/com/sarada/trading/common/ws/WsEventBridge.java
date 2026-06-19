package com.sarada.trading.common.ws;

import com.sarada.trading.common.events.DomainEvents;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Relays infrastructure-level domain events onto dashboard topics. */
@Component
@RequiredArgsConstructor
public class WsEventBridge {

    private final WsPublisher ws;

    public record ConnectionView(String state, String detail, Instant at) {}

    @EventListener
    public void onConnectionChanged(DomainEvents.BrokerConnectionChanged event) {
        ws.publish(WsPublisher.TOPIC_CONNECTION,
                new ConnectionView(event.state(), event.detail(), Instant.now()));
        ws.feed("CONNECTION", "Feed " + event.state().toLowerCase(), event.detail());
    }
}
