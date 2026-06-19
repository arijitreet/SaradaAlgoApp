package com.sarada.trading.common.market;

import java.math.BigDecimal;
import java.time.Instant;

/** A completed OHLCV candle. */
public record Candle(
        long instrumentToken,
        String symbol,
        int intervalMinutes,
        Instant openTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume
) {
    public BigDecimal typicalPrice() {
        return high.add(low).add(close)
                .divide(BigDecimal.valueOf(3), 4, java.math.RoundingMode.HALF_UP);
    }

    public BigDecimal range() {
        return high.subtract(low);
    }
}
