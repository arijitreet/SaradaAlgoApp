package com.sarada.trading.broker.application;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Registry of instrument tokens the platform wants live ticks for.
 * Feed implementations (Kite ticker / simulator) observe this set, so
 * subscriptions survive reconnects and feed swaps.
 */
@Component
public class FeedSubscriptions {

    private final Set<Long> tokens = ConcurrentHashMap.newKeySet();
    private final CopyOnWriteArrayList<Consumer<Long>> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(long token) {
        if (tokens.add(token)) {
            listeners.forEach(listener -> listener.accept(token));
        }
    }

    public Set<Long> all() {
        return Set.copyOf(tokens);
    }

    public void onSubscribe(Consumer<Long> listener) {
        listeners.add(listener);
    }
}
