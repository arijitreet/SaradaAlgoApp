package com.sarada.trading.marketdata.application;

import com.sarada.trading.broker.infra.kite.KiteClientFactory;
import com.sarada.trading.common.config.AppProperties;
import com.sarada.trading.common.time.TradingClock;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.models.HistoricalData;
import com.zerodhatech.models.Instrument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Service
public class StockScannerService {

    private static final BigDecimal MIN_GAP_PCT = BigDecimal.valueOf(5);
    private static final int REQUIRED_CANDLES = 16;
    private static final long RATE_LIMIT_MS = 200;

    private final KiteClientFactory kiteClientFactory;
    private final TradingClock clock;
    private final AppProperties props;
    private final Executor executor;

    private volatile ScanResult cachedResult = ScanResult.EMPTY;
    private final AtomicBoolean scanning = new AtomicBoolean(false);

    public StockScannerService(
            KiteClientFactory kiteClientFactory,
            TradingClock clock,
            AppProperties props,
            @Qualifier("appExecutor") Executor executor) {
        this.kiteClientFactory = kiteClientFactory;
        this.clock = clock;
        this.props = props;
        this.executor = executor;
    }

    // ── Public API ──

    public ScanResponse getCachedResult() {
        return new ScanResponse(cachedResult, scanning.get());
    }

    public boolean triggerScan() {
        if (!scanning.compareAndSet(false, true)) {
            return false;
        }
        executor.execute(() -> {
            try {
                doScan();
            } catch (Exception e) {
                log.error("Stock scan failed: {}", e.getMessage(), e);
                cachedResult = new ScanResult(List.of(), Instant.now(), 0, e.getMessage());
            } finally {
                scanning.set(false);
            }
        });
        return true;
    }

    @Scheduled(cron = "0 20 9 * * MON-FRI", zone = "Asia/Kolkata")
    public void scheduledScan() {
        log.info("Running scheduled gap-down scan");
        triggerScan();
    }

    // ── Internals ──

    private void doScan() {
        KiteConnect kite = kiteClientFactory.authenticated().orElse(null);
        if (kite == null) {
            log.warn("Gap-down scan skipped: Kite not authenticated");
            cachedResult = new ScanResult(List.of(), Instant.now(), 0, "Kite not authenticated");
            return;
        }

        Map<String, Long> tokenMap;
        try {
            tokenMap = resolveTokens(kite);
        } catch (Exception | com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException e) {
            log.error("Failed to resolve instrument tokens: {}", e.getMessage());
            cachedResult = new ScanResult(List.of(), Instant.now(), 0, "Instrument lookup failed");
            return;
        }

        LocalDate today = clock.tradingDay();
        Date from = toDate(today.minusDays(30));
        Date to = toDate(today.plusDays(1));

        List<GapDownStock> matches = new ArrayList<>();
        int scanned = 0;

        for (String symbol : Nifty200Stocks.SYMBOLS) {
            Long token = tokenMap.get(symbol);
            if (token == null) {
                continue;
            }
            scanned++;

            try {
                HistoricalData data = kite.getHistoricalData(
                        from, to, String.valueOf(token), "day", false, false);
                Thread.sleep(RATE_LIMIT_MS);

                List<HistoricalData> candles = data.dataArrayList;
                if (candles == null || candles.size() < REQUIRED_CANDLES) {
                    continue;
                }

                int size = candles.size();
                List<HistoricalData> recent = candles.subList(size - REQUIRED_CANDLES, size);

                evaluate(symbol, recent).ifPresent(matches::add);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Scan interrupted at symbol {}", symbol);
                break;
            } catch (Exception | com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException e) {
                log.debug("Scan failed for {}: {}", symbol, e.getMessage());
            }
        }

        cachedResult = new ScanResult(matches, Instant.now(), scanned, null);
        log.info("Gap-down scan complete: {}/{} stocks scanned, {} matches",
                scanned, Nifty200Stocks.SYMBOLS.size(), matches.size());
    }

    private Optional<GapDownStock> evaluate(String symbol, List<HistoricalData> candles) {
        HistoricalData seventhDay = candles.get(0);
        HistoricalData todayCandle = candles.get(REQUIRED_CANDLES - 1);

        BigDecimal ltp = BigDecimal.valueOf(todayCandle.close);
        BigDecimal todayOpen = BigDecimal.valueOf(todayCandle.open);
        BigDecimal fifteenthDayClose = BigDecimal.valueOf(seventhDay.close);

        BigDecimal fifteenDayHigh = BigDecimal.ZERO;
        BigDecimal fifteenDayLow = BigDecimal.valueOf(Double.MAX_VALUE);

        for (int i = 0; i < REQUIRED_CANDLES - 1; i++) {
            BigDecimal low = BigDecimal.valueOf(candles.get(i).low);
            BigDecimal high = BigDecimal.valueOf(candles.get(i).high);

            if (ltp.compareTo(low) >= 0) {
                return Optional.empty();
            }
            if (high.compareTo(fifteenDayHigh) > 0) fifteenDayHigh = high;
            if (low.compareTo(fifteenDayLow) < 0) fifteenDayLow = low;
        }

        if (fifteenthDayClose.signum() == 0) {
            return Optional.empty();
        }
        BigDecimal gapPct = fifteenthDayClose.subtract(todayOpen)
                .multiply(BigDecimal.valueOf(100))
                .divide(fifteenthDayClose, 2, RoundingMode.HALF_UP);

        if (gapPct.compareTo(MIN_GAP_PCT) < 0) {
            return Optional.empty();
        }

        return Optional.of(new GapDownStock(
                symbol, ltp, todayOpen, fifteenthDayClose,
                gapPct, fifteenDayHigh, fifteenDayLow));
    }

    private Map<String, Long> resolveTokens(KiteConnect kite)
            throws Exception, com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException {
        Set<String> wanted = new HashSet<>(Nifty200Stocks.SYMBOLS);
        List<Instrument> instruments = kite.getInstruments("NSE");

        return instruments.stream()
                .filter(i -> "EQ".equals(i.instrument_type))
                .filter(i -> wanted.contains(i.tradingsymbol))
                .collect(Collectors.toMap(
                        i -> i.tradingsymbol,
                        i -> i.instrument_token,
                        (a, b) -> a));
    }

    private Date toDate(LocalDate date) {
        return Date.from(date.atStartOfDay(props.zone()).toInstant());
    }

    // ── DTOs ──

    public record GapDownStock(
            String symbol,
            BigDecimal ltp,
            BigDecimal todayOpen,
            BigDecimal fifteenthDayClose,
            BigDecimal gapDownPercent,
            BigDecimal fifteenDayHigh,
            BigDecimal fifteenDayLow
    ) {}

    public record ScanResult(
            List<GapDownStock> stocks,
            Instant scanTime,
            int scanned,
            String error
    ) {
        static final ScanResult EMPTY = new ScanResult(List.of(), null, 0, null);
    }

    public record ScanResponse(
            List<GapDownStock> stocks,
            Instant scanTime,
            int scanned,
            boolean scanning,
            String error
    ) {
        ScanResponse(ScanResult result, boolean scanning) {
            this(result.stocks(), result.scanTime(), result.scanned(), scanning, result.error());
        }
    }
}
