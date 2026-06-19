package com.sarada.trading.marketdata.application;

import com.sarada.trading.broker.infra.kite.KiteClientFactory;
import com.sarada.trading.common.config.AppProperties;
import com.sarada.trading.common.events.DomainEvents;
import com.sarada.trading.common.market.Candle;
import com.sarada.trading.common.time.TradingClock;
import com.sarada.trading.marketdata.domain.CandleEntity;
import com.sarada.trading.marketdata.infra.CandleRepository;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.models.HistoricalData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Backfills missed candles from Kite's historical-data API whenever the feed
 * (re)connects after the trading day's first candle (09:15-09:20) has already
 * elapsed - typically because the daily Kite re-login happens after market open.
 *
 * Replayed candles are published as {@link DomainEvents.HistoricalCandleClosed} so
 * strategies capture the true 09:15 high/low and warm up their indicators (EMA,
 * VWAP, ATR, support/resistance) without placing trades off stale data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistoricalCandleBackfillService {

    private static final DateTimeFormatter KITE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXX");

    private final InstrumentService instrumentService;
    private final KiteClientFactory clientFactory;
    private final CandleRepository candleRepository;
    private final ApplicationEventPublisher events;
    private final AppProperties props;
    private final TradingClock clock;

    /** Set once history has been replayed into the strategies this run, so a later
     *  WS reconnect doesn't replay (and double-count) the same candles into the indicators. */
    private final AtomicBoolean backfillDone = new AtomicBoolean(false);

    @EventListener
    @Async("appExecutor")
    public void onBrokerConnectionChanged(DomainEvents.BrokerConnectionChanged event) {
        if (!"CONNECTED".equals(event.state()) || backfillDone.get()) {
            return;
        }
        try {
            backfill();
        } catch (Exception | com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException e) {
            log.error("Historical candle backfill failed: {}", e.getMessage());
        }
    }

    private void backfill() throws Exception, com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException {
        ZonedDateTime now = clock.now();
        if (now.getDayOfWeek() == DayOfWeek.SATURDAY || now.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return;
        }
        var trading = props.trading();
        var firstCandleEnd = trading.firstCandleStart().plusMinutes(trading.candleMinutes());
        if (now.toLocalTime().isBefore(firstCandleEnd) || now.toLocalTime().isAfter(trading.sessionEnd())) {
            return; // live feed will capture the first candle itself, or it's outside trading hours
        }

        long instrumentToken = instrumentService.underlyingToken();
        String symbol = trading.underlying();
        int intervalMinutes = trading.candleMinutes();

        LocalDateTime fromWallClock = now.toLocalDate().atTime(trading.firstCandleStart());
        Instant currentBucketStart = bucketOf(now.toInstant(), intervalMinutes);
        LocalDateTime toWallClock = LocalDateTime.ofInstant(currentBucketStart, props.zone());
        if (!toWallClock.isAfter(fromWallClock)) {
            return; // nothing has closed since 09:15 yet
        }

        KiteConnect kite = clientFactory.authenticated().orElse(null);
        if (kite == null) {
            return;
        }

        String interval = intervalMinutes == 1 ? "minute" : intervalMinutes + "minute";
        HistoricalData data = kite.getHistoricalData(
                toKiteDate(fromWallClock), toKiteDate(toWallClock),
                String.valueOf(instrumentToken), interval, false, false);

        int replayed = 0;
        int persisted = 0;
        for (HistoricalData point : data.dataArrayList) {
            Instant openTime = ZonedDateTime.parse(point.timeStamp, KITE_TIMESTAMP).toInstant();
            if (!openTime.isBefore(currentBucketStart)) {
                continue; // still-forming candle - the live feed owns this one
            }
            Candle candle = new Candle(instrumentToken, symbol, intervalMinutes, openTime,
                    BigDecimal.valueOf(point.open), BigDecimal.valueOf(point.high),
                    BigDecimal.valueOf(point.low), BigDecimal.valueOf(point.close), point.volume);
            // Always replay so indicators (EMA/ATR/VWAP/SR) warm up on this fresh process,
            // but only persist candles the DB doesn't already have (e.g. from an earlier paper run).
            if (!candleRepository.existsByInstrumentTokenAndIntervalMinutesAndOpenTime(
                    instrumentToken, intervalMinutes, openTime)) {
                candleRepository.save(CandleEntity.from(candle));
                persisted++;
            }
            events.publishEvent(new DomainEvents.HistoricalCandleClosed(candle));
            replayed++;
        }
        if (replayed > 0) {
            backfillDone.set(true);
        }
        log.info("Replayed {} historical candle(s) ({} newly persisted) for {} to warm up indicators ({} -> {})",
                replayed, persisted, symbol, fromWallClock, toWallClock);
    }

    private static Instant bucketOf(Instant ts, int intervalMinutes) {
        long intervalSeconds = intervalMinutes * 60L;
        long epoch = ts.getEpochSecond();
        return Instant.ofEpochSecond(epoch - (epoch % intervalSeconds));
    }

    /** Round-trips an IST wall-clock time through the JVM default zone so Kite's
     *  "yyyy-MM-dd HH:mm:ss" formatter (which always uses the default zone) emits it verbatim. */
    private static Date toKiteDate(LocalDateTime istWallClock) {
        return Date.from(istWallClock.atZone(ZoneId.systemDefault()).toInstant());
    }
}
