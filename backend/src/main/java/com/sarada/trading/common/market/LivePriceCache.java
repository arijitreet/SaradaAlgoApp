package com.sarada.trading.common.market;

import com.sarada.trading.common.events.DomainEvents;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory last-traded-price cache, fed by the tick stream. */
@Component
public class LivePriceCache {

    private final Map<Long, BigDecimal> lastPrice = new ConcurrentHashMap<>();

    @EventListener
    public void onTick(DomainEvents.TickReceived event) {
        lastPrice.put(event.tick().instrumentToken(), event.tick().lastPrice());
    }

    public Optional<BigDecimal> ltp(long instrumentToken) {
        return Optional.ofNullable(lastPrice.get(instrumentToken));
    }
}
