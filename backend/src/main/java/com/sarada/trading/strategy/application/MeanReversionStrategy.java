package com.sarada.trading.strategy.application;

import com.sarada.trading.common.config.AppProperties;
import com.sarada.trading.common.market.Candle;
import com.sarada.trading.common.market.IndicatorSnapshot;
import com.sarada.trading.common.market.SignalType;
import com.sarada.trading.common.market.TradeSignal;
import com.sarada.trading.common.time.TradingClock;
import com.sarada.trading.strategy.domain.TradingStrategy;
import com.sarada.trading.strategy.domain.indicators.Adx;
import com.sarada.trading.strategy.domain.indicators.BollingerBands;
import com.sarada.trading.strategy.domain.indicators.Rsi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Mean Reversion (5-minute Nifty candles) — fade band extremes in a ranging market.
 *
 *  Indicators: Bollinger Bands (20 SMA, 2 SD) + RSI(14) + ADX(14).
 *
 *  Entry — only when ADX &lt; threshold (ranging, skip trends):
 *  ─────────────────────────────────────────────────────────
 *  • BUY CE: close ≤ lower band  AND  RSI &lt; oversold.
 *  • BUY PE: close ≥ upper band  AND  RSI &gt; overbought.
 *
 *  Exits are evaluated on the UNDERLYING INDEX (see {@code usesIndexExit}):
 *  ─────────────────────────────────────────────────────────────────────
 *  • Target → middle band (20 SMA): mean reversion completes.
 *  • Stop   → entry ∓ (slBbWidthMult × BB width at entry).
 *  The index levels travel on the signal and are enforced tick-by-tick by
 *  PositionMonitor against the live index price — option-premium points are not
 *  used, since BB/RSI/ADX all live in index space.
 *
 *  A fired side is re-armed only once price returns inside the bands, so a price
 *  that hugs a band does not re-fire every candle.
 */
@Slf4j
@Component
public class MeanReversionStrategy implements TradingStrategy {

    public static final String ID = "mean-reversion-v1";

    private final AppProperties props;
    private final TradingClock clock;

    private final BollingerBands bb;
    private final Rsi rsi;
    private final Adx adx;

    private boolean armed = true;     // false after a fire until price returns inside the bands
    private int candlesProcessed;
    private String lastEvaluation = "Warming up";

    public MeanReversionStrategy(AppProperties props, TradingClock clock) {
        this.props = props;
        this.clock = clock;
        var cfg = props.strategy().meanReversion();
        this.bb = new BollingerBands(cfg.bbPeriod(), cfg.bbStdDev());
        this.rsi = new Rsi(cfg.rsiPeriod());
        this.adx = new Adx(cfg.adxPeriod());
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
        BigDecimal close = candle.close();
        bb.update(close);
        rsi.update(close);
        adx.update(candle);
        candlesProcessed++;

        if (!bb.isReady() || !rsi.isReady() || !adx.isReady()) {
            lastEvaluation = "Warming up: " + candlesProcessed + " candles";
            return Optional.empty();
        }

        var cfg = props.strategy().meanReversion();
        BigDecimal upper = bb.upper();
        BigDecimal lower = bb.lower();
        BigDecimal mid = bb.middle();
        BigDecimal rsiVal = rsi.value();
        BigDecimal adxVal = adx.value();

        // Re-arm once price is back inside the bands.
        if (!armed && close.compareTo(lower) > 0 && close.compareTo(upper) < 0) {
            armed = true;
        }

        boolean ranging = adxVal.compareTo(cfg.adxThreshold()) < 0;
        if (!ranging) {
            lastEvaluation = String.format("Trending — ADX %.1f ≥ %.1f, no trade", adxVal, cfg.adxThreshold());
            return Optional.empty();
        }

        boolean atLowerBand = close.compareTo(lower) <= 0;
        boolean atUpperBand = close.compareTo(upper) >= 0;
        boolean oversold = rsiVal.compareTo(cfg.rsiOversold()) < 0;
        boolean overbought = rsiVal.compareTo(cfg.rsiOverbought()) > 0;

        BigDecimal slDistance = cfg.slBbWidthMult().multiply(bb.width());

        if (armed && atLowerBand && oversold) {
            armed = false;
            BigDecimal indexStop = close.subtract(slDistance);
            lastEvaluation = String.format("BUY_CE: close %.2f ≤ lowerBB %.2f, RSI %.1f, ADX %.1f",
                    close, lower, rsiVal, adxVal);
            log.info("[MR] BUY CE — close {} at lower band, RSI {}, ADX {} | target(midBB)={} stop={}",
                    close, rsiVal, adxVal, mid, indexStop);
            return Optional.of(signal(SignalType.BUY_CE, close,
                    "Lower-band reversion: close " + close + " ≤ BB lower " + lower
                            + ", RSI " + rsiVal + " < " + cfg.rsiOversold() + ", ADX " + adxVal + " (ranging)",
                    indexStop, mid));
        }

        if (armed && atUpperBand && overbought) {
            armed = false;
            BigDecimal indexStop = close.add(slDistance);
            lastEvaluation = String.format("BUY_PE: close %.2f ≥ upperBB %.2f, RSI %.1f, ADX %.1f",
                    close, upper, rsiVal, adxVal);
            log.info("[MR] BUY PE — close {} at upper band, RSI {}, ADX {} | target(midBB)={} stop={}",
                    close, rsiVal, adxVal, mid, indexStop);
            return Optional.of(signal(SignalType.BUY_PE, close,
                    "Upper-band reversion: close " + close + " ≥ BB upper " + upper
                            + ", RSI " + rsiVal + " > " + cfg.rsiOverbought() + ", ADX " + adxVal + " (ranging)",
                    indexStop, mid));
        }

        lastEvaluation = String.format("No setup: close=%.2f BB[%.2f..%.2f] RSI=%.1f ADX=%.1f",
                close, lower, upper, rsiVal, adxVal);
        return Optional.empty();
    }

    private TradeSignal signal(SignalType type, BigDecimal trigger, String reason,
                               BigDecimal indexStopLoss, BigDecimal indexTarget) {
        return new TradeSignal(ID, type, underlying(), trigger, snapshot(), reason,
                clock.now().toInstant(), props.strategy().meanReversion().strikeOffset(),
                indexStopLoss, indexTarget);
    }

    @Override
    public IndicatorSnapshot snapshot() {
        return new IndicatorSnapshot(
                null, null, null, null, null, null, null, null, null, null,
                bb.upper(), bb.middle(), bb.lower(), rsi.value(), adx.value());
    }

    @Override
    public Health health() {
        boolean ready = bb.isReady() && rsi.isReady() && adx.isReady();
        var cfg = props.strategy().meanReversion();
        boolean ranging = ready && adx.value().compareTo(cfg.adxThreshold()) < 0;
        BigDecimal close = bb.middle();   // proxy for "centred" display only
        boolean atLower = ready && bb.lower() != null && close != null && close.compareTo(bb.lower()) <= 0;
        boolean atUpper = ready && bb.upper() != null && close != null && close.compareTo(bb.upper()) >= 0;
        return new Health(
                /*firstCandleCaptured=*/ ready,
                /*candlesProcessed=*/   candlesProcessed,
                /*indicatorsReady=*/    ready,
                /*emaBullish=*/         atLower,    // reused: "at lower band"
                /*emaBearish=*/         atUpper,    // reused: "at upper band"
                /*aboveVwap=*/          false,
                /*belowVwap=*/          false,
                /*atrPass=*/            ranging,    // reused: "ADX < threshold (ranging)"
                /*lastEvaluation=*/     lastEvaluation);
    }

    @Override
    public void reset() {
        bb.reset();
        rsi.reset();
        adx.reset();
        armed = true;
        candlesProcessed = 0;
        lastEvaluation = "Reset for new trading day";
    }
}
