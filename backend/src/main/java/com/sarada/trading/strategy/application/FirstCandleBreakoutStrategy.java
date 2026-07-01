package com.sarada.trading.strategy.application;

import com.sarada.trading.common.config.AppProperties;
import com.sarada.trading.common.market.Candle;
import com.sarada.trading.common.market.IndicatorSnapshot;
import com.sarada.trading.common.market.SignalType;
import com.sarada.trading.common.market.TradeSignal;
import com.sarada.trading.common.time.TradingClock;
import com.sarada.trading.strategy.domain.TradingStrategy;
import com.sarada.trading.strategy.domain.indicators.Atr;
import com.sarada.trading.strategy.domain.indicators.Ema;
import com.sarada.trading.strategy.domain.indicators.RollingSupportResistance;
import com.sarada.trading.strategy.domain.indicators.Vwap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * First-Candle Breakout v1 (5-minute Nifty candles).
 *
 *  Entry rules
 *  ───────────
 *  • Nothing trades until the 09:15–09:20 candle has completed.
 *  • BUY_CE: close crosses above first-candle high  AND EMA9 > EMA15
 *            AND close > VWAP AND ATR ≥ min-ATR filter.
 *  • BUY_PE: close crosses below first-candle low   AND EMA9 < EMA15
 *            AND close < VWAP AND ATR ≥ min-ATR filter.
 *
 *  "Crosses" is evaluated against the previous close, so a level fires once
 *  per breakout rather than on every candle that stays beyond it.
 */
@Slf4j
@Component
public class FirstCandleBreakoutStrategy implements TradingStrategy {

    public static final String ID = "first-candle-breakout-v1";

    private final AppProperties props;
    private final TradingClock clock;

    private final Ema emaFast;
    private final Ema emaSlow;
    private final Vwap vwap = new Vwap();
    private final Atr atr;
    private final RollingSupportResistance supportResistance;

    private BigDecimal firstCandleHigh;
    private BigDecimal firstCandleLow;
    private BigDecimal previousClose;
    private int candlesProcessed;
    private String lastEvaluation = "Waiting for first candle";

    public FirstCandleBreakoutStrategy(AppProperties props, TradingClock clock) {
        this.props = props;
        this.clock = clock;
        var cfg = props.strategy().firstCandleBreakout();
        this.emaFast = new Ema(cfg.emaFast());
        this.emaSlow = new Ema(cfg.emaSlow());
        this.atr = new Atr(cfg.atrPeriod());
        this.supportResistance = new RollingSupportResistance(cfg.srLookback());
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String underlying() {
        return props.trading().underlying();
    }

    @Override
    public Optional<TradeSignal> onCandle(Candle candle) {
        BigDecimal prevClose = previousClose;
        updateIndicators(candle);
        candlesProcessed++;

        // If the feed connects after 09:20 (e.g. daily Kite re-login done late), no candle will
        // ever have openTime == 09:15 — fall back to the first candle actually seen so the
        // strategy still gets a breakout reference instead of waiting forever.
        boolean isScheduledFirstCandle = clock.isFirstCandle(candle.openTime());
        boolean feedStartedLate = firstCandleHigh == null && candlesProcessed == 1 && !isScheduledFirstCandle;

        if (isScheduledFirstCandle || feedStartedLate) {
            firstCandleHigh = candle.high();
            firstCandleLow = candle.low();
            if (feedStartedLate) {
                lastEvaluation = "First candle (09:15-09:20) missed - feed connected late; using "
                        + clock.localTime(candle.openTime()) + " candle as reference H=" + firstCandleHigh
                        + " L=" + firstCandleLow;
                log.warn("First candle (09:15-09:20) missed - feed connected late. Using candle at {} as reference: high={} low={}",
                        clock.localTime(candle.openTime()), firstCandleHigh, firstCandleLow);
            } else {
                lastEvaluation = "First candle captured H=" + firstCandleHigh + " L=" + firstCandleLow;
                log.info("First candle captured: high={} low={}", firstCandleHigh, firstCandleLow);
            }
            return Optional.empty();
        }
        if (firstCandleHigh == null || prevClose == null) {
            lastEvaluation = "Waiting for first candle (09:15–09:20) to complete";
            return Optional.empty();
        }

        BigDecimal close = candle.close();
        BigDecimal fast = emaFast.value();
        BigDecimal slow = emaSlow.value();
        BigDecimal vwapValue = vwap.value();
        BigDecimal atrValue = atr.value();
        BigDecimal minAtr = props.strategy().firstCandleBreakout().minAtrPoints();

        boolean atrPass = atrValue != null && atrValue.compareTo(minAtr) >= 0;
        boolean emaBullish = fast != null && slow != null && fast.compareTo(slow) > 0;
        boolean emaBearish = fast != null && slow != null && fast.compareTo(slow) < 0;
        boolean aboveVwap = vwapValue != null && close.compareTo(vwapValue) > 0;
        boolean belowVwap = vwapValue != null && close.compareTo(vwapValue) < 0;

        boolean brokeHigh = prevClose.compareTo(firstCandleHigh) <= 0
                && close.compareTo(firstCandleHigh) > 0;
        boolean brokeLow = prevClose.compareTo(firstCandleLow) >= 0
                && close.compareTo(firstCandleLow) < 0;

        if (brokeHigh && emaBullish && aboveVwap && atrPass) {
            lastEvaluation = "BUY_CE fired @ " + close;
            return Optional.of(signal(SignalType.BUY_CE, close,
                    "First-candle high " + firstCandleHigh + " broken; EMA9>EMA15; price>VWAP; ATR=" + atrValue));
        }
        if (brokeLow && emaBearish && belowVwap && atrPass) {
            lastEvaluation = "BUY_PE fired @ " + close;
            return Optional.of(signal(SignalType.BUY_PE, close,
                    "First-candle low " + firstCandleLow + " broken; EMA9<EMA15; price<VWAP; ATR=" + atrValue));
        }

        lastEvaluation = String.format("No setup: brokeHigh=%s brokeLow=%s emaBull=%s aboveVwap=%s atrPass=%s",
                brokeHigh, brokeLow, emaBullish, aboveVwap, atrPass);
        return Optional.empty();
    }

    private void updateIndicators(Candle candle) {
        emaFast.update(candle.close());
        emaSlow.update(candle.close());
        vwap.update(candle);
        atr.update(candle);
        supportResistance.update(candle);
        previousClose = candle.close();
    }

    private TradeSignal signal(SignalType type, BigDecimal trigger, String reason) {
        return new TradeSignal(ID, type, underlying(), trigger, snapshot(), reason,
                clock.now().toInstant(), 0, null, null);
    }

    @Override
    public IndicatorSnapshot snapshot() {
        return new IndicatorSnapshot(
                emaFast.value(), emaSlow.value(), vwap.value(), atr.value(),
                supportResistance.support(), supportResistance.resistance(),
                firstCandleHigh, firstCandleLow,
                null, null,                 // supertrendLine, supertrendBullish — N/A
                null, null, null, null, null);  // bb / rsi / adx — N/A
    }

    @Override
    public Health health() {
        BigDecimal fast = emaFast.value();
        BigDecimal slow = emaSlow.value();
        BigDecimal vwapValue = vwap.value();
        BigDecimal atrValue = atr.value();
        BigDecimal minAtr = props.strategy().firstCandleBreakout().minAtrPoints();
        return new Health(
                firstCandleHigh != null,
                candlesProcessed,
                emaFast.isReady() && emaSlow.isReady() && atr.isReady(),
                fast != null && slow != null && fast.compareTo(slow) > 0,
                fast != null && slow != null && fast.compareTo(slow) < 0,
                vwapValue != null && previousClose != null && previousClose.compareTo(vwapValue) > 0,
                vwapValue != null && previousClose != null && previousClose.compareTo(vwapValue) < 0,
                atrValue != null && atrValue.compareTo(minAtr) >= 0,
                lastEvaluation);
    }

    @Override
    public void reset() {
        emaFast.reset();
        emaSlow.reset();
        vwap.reset();
        atr.reset();
        supportResistance.reset();
        firstCandleHigh = null;
        firstCandleLow = null;
        previousClose = null;
        candlesProcessed = 0;
        lastEvaluation = "Reset for new trading day";
    }
}
