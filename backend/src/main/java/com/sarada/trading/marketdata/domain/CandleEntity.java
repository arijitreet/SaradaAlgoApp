package com.sarada.trading.marketdata.domain;

import com.sarada.trading.common.market.Candle;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "candles")
@Getter
@NoArgsConstructor
public class CandleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instrument_token", nullable = false)
    private long instrumentToken;

    @Column(nullable = false)
    private String symbol;

    @Column(name = "interval_minutes", nullable = false)
    private int intervalMinutes;

    @Column(name = "open_time", nullable = false)
    private Instant openTime;

    @Column(nullable = false)
    private BigDecimal open;

    @Column(nullable = false)
    private BigDecimal high;

    @Column(nullable = false)
    private BigDecimal low;

    @Column(nullable = false)
    private BigDecimal close;

    @Column(nullable = false)
    private long volume;

    public static CandleEntity from(Candle candle) {
        CandleEntity entity = new CandleEntity();
        entity.instrumentToken = candle.instrumentToken();
        entity.symbol = candle.symbol();
        entity.intervalMinutes = candle.intervalMinutes();
        entity.openTime = candle.openTime();
        entity.open = candle.open();
        entity.high = candle.high();
        entity.low = candle.low();
        entity.close = candle.close();
        entity.volume = candle.volume();
        return entity;
    }

    public Candle toDomain() {
        return new Candle(instrumentToken, symbol, intervalMinutes, openTime,
                open, high, low, close, volume);
    }
}
