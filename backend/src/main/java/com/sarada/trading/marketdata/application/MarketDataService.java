package com.sarada.trading.marketdata.application;

import com.sarada.trading.broker.application.FeedSubscriptions;
import com.sarada.trading.common.config.AppProperties;
import com.sarada.trading.common.events.DomainEvents;
import com.sarada.trading.common.ws.WsPublisher;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bootstraps the underlying-index feed and streams throttled market updates
 * to the dashboard.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataService {

    private final InstrumentService instrumentService;
    private final FeedSubscriptions subscriptions;
    private final CandleAggregator aggregator;
    private final WsPublisher ws;
    private final AppProperties props;

    @Getter
    private volatile long underlyingToken;

    private volatile BigDecimal dayOpen;
    private final Map<Long, Long> lastPushAt = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        underlyingToken = instrumentService.underlyingToken();
        aggregator.track(underlyingToken, props.trading().underlying());
        subscriptions.subscribe(underlyingToken);
        log.info("Tracking underlying {} (token {})", props.trading().underlying(), underlyingToken);
    }

    public record MarketUpdate(long token, String symbol, BigDecimal ltp,
                               BigDecimal dayChange, BigDecimal dayChangePct, Instant at) {}

    @EventListener
    public void onTick(DomainEvents.TickReceived event) {
        long token = event.tick().instrumentToken();
        if (token != underlyingToken) {
            return;
        }
        if (dayOpen == null) {
            dayOpen = event.tick().lastPrice();
        }
        // throttle UI pushes to 2/sec per token
        long now = System.currentTimeMillis();
        Long last = lastPushAt.get(token);
        if (last != null && now - last < 500) {
            return;
        }
        lastPushAt.put(token, now);

        BigDecimal ltp = event.tick().lastPrice();
        BigDecimal change = ltp.subtract(dayOpen);
        BigDecimal changePct = dayOpen.signum() == 0 ? BigDecimal.ZERO
                : change.multiply(BigDecimal.valueOf(100)).divide(dayOpen, 2, java.math.RoundingMode.HALF_UP);
        ws.publish(WsPublisher.TOPIC_MARKET,
                new MarketUpdate(token, props.trading().underlying(), ltp, change, changePct,
                        event.tick().timestamp()));
    }
}
