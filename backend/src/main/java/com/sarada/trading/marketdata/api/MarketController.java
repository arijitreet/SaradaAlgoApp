package com.sarada.trading.marketdata.api;

import com.sarada.trading.common.market.Candle;
import com.sarada.trading.common.market.LivePriceCache;
import com.sarada.trading.marketdata.application.MarketDataService;
import com.sarada.trading.marketdata.domain.CandleEntity;
import com.sarada.trading.marketdata.infra.CandleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketController {

    private final CandleRepository candles;
    private final MarketDataService marketDataService;
    private final LivePriceCache priceCache;

    /** Recent candles for the underlying, oldest → newest (chart seed). */
    @GetMapping("/candles")
    public List<Candle> candles(@RequestParam(defaultValue = "100") int limit) {
        return candles.findByInstrumentTokenAndIntervalMinutesOrderByOpenTimeDesc(
                        marketDataService.getUnderlyingToken(), 5,
                        PageRequest.of(0, Math.min(limit, 500)))
                .stream()
                .map(CandleEntity::toDomain)
                .sorted(Comparator.comparing(Candle::openTime))
                .toList();
    }

    public record Snapshot(long underlyingToken, BigDecimal ltp) {}

    @GetMapping("/snapshot")
    public Snapshot snapshot() {
        long token = marketDataService.getUnderlyingToken();
        return new Snapshot(token, priceCache.ltp(token).orElse(null));
    }
}
