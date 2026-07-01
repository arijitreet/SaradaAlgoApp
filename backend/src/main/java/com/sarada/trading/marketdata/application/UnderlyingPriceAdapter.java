package com.sarada.trading.marketdata.application;

import com.sarada.trading.common.market.LivePriceCache;
import com.sarada.trading.positions.domain.UnderlyingPricePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * marketdata-side implementation of the positions module's {@link UnderlyingPricePort}.
 * Resolves the underlying index token once (it is constant for the run — the lookup
 * hits Kite) and then reads its live LTP from the in-memory price cache per call.
 */
@Component
@RequiredArgsConstructor
public class UnderlyingPriceAdapter implements UnderlyingPricePort {

    private final LivePriceCache priceCache;
    private final InstrumentService instrumentService;

    private volatile long cachedToken = 0L;

    @Override
    public Optional<BigDecimal> underlyingLtp() {
        long token = cachedToken;
        if (token == 0L) {
            token = instrumentService.underlyingToken();
            cachedToken = token;
        }
        return priceCache.ltp(token);
    }
}
