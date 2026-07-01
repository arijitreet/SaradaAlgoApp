package com.sarada.trading.strategy.application;

import com.sarada.trading.common.config.AppProperties;
import com.sarada.trading.common.market.Candle;
import com.sarada.trading.common.market.IndicatorSnapshot;
import com.sarada.trading.common.market.SignalType;
import com.sarada.trading.common.market.TradeSignal;
import com.sarada.trading.common.time.TradingClock;
import com.sarada.trading.strategy.domain.TradingStrategy;
import com.sarada.trading.strategy.domain.indicators.Rsi;
import com.sarada.trading.strategy.domain.indicators.Vwap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * VWAP Reversion + Breakout (5-minute Nifty candles). VWAP and its ±σ bands reset
 * daily at 09:15 via {@link #reset()}. Exits are evaluated on the underlying index
 * (see PositionMonitor): targets/stops are VWAP-relative index levels, not premium.
 *
 *  Sub-strategy A — Reversion (VWAP reclaim/loss):
 *  ──────────────────────────────────────────────
 *  • BUY CE: price closes back ABOVE VWAP (was below) AND RSI > buyMin.
 *            Target = VWAP + 1σ, stop = VWAP − 1σ.
 *  • BUY PE: price closes back BELOW VWAP (was above) AND RSI < sellMax.
 *            Target = VWAP − 1σ, stop = VWAP + 1σ.
 *
 *  Sub-strategy B — Breakout (holds N consecutive candles past VWAP):
 *  ────────────────────────────────────────────────────────────────
 *  • BUY CE: N consecutive closes ABOVE VWAP.
 *            Target = VWAP + (VWAP − day low), stop = VWAP.
 *  • BUY PE: N consecutive closes BELOW VWAP.
 *            Target = VWAP − (day high − VWAP), stop = VWAP.
 *
 *  Volume confirmation is omitted (index has no traded volume). Entries beyond
 *  ±highProbStdDev are flagged "high-probability" in the signal reason.
 *
 *  Interpretation note: the spec lists the reversion target as "VWAP mid line",
 *  but since the entry IS the VWAP cross, that target is degenerate; the meaningful
 *  reversion objective is the ±1σ band (used here). Breakout stops are inferred as
 *  the VWAP reclaim level since the spec specifies a target but no stop.
 */
@Slf4j
@Component
public class VwapStrategy implements TradingStrategy {

    public static final String ID = "vwap-strategy-v1";

    private final AppProperties props;
    private final TradingClock clock;

    private final Vwap vwap = new Vwap();
    private final Rsi rsi;

    private Boolean prevAboveVwap;
    private int consecutiveAbove;
    private int consecutiveBelow;
    private BigDecimal dayHigh;
    private BigDecimal dayLow;
    private int candlesProcessed;
    private String lastEvaluation = "Warming up";

    public VwapStrategy(AppProperties props, TradingClock clock) {
        this.props = props;
        this.clock = clock;
        this.rsi = new Rsi(props.strategy().vwapStrategy().rsiPeriod());
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
        vwap.update(candle);
        rsi.update(close);
        dayHigh = dayHigh == null ? candle.high() : dayHigh.max(candle.high());
        dayLow = dayLow == null ? candle.low() : dayLow.min(candle.low());
        candlesProcessed++;

        BigDecimal vwapValue = vwap.value();
        if (vwapValue == null || !rsi.isReady()) {
            lastEvaluation = "Warming up: " + candlesProcessed + " candles";
            return Optional.empty();
        }

        var cfg = props.strategy().vwapStrategy();
        BigDecimal sd = vwap.stdDev();
        BigDecimal rsiVal = rsi.value();

        boolean aboveVwap = close.compareTo(vwapValue) > 0;
        boolean bullishCross = prevAboveVwap != null && !prevAboveVwap && aboveVwap;
        boolean bearishCross = prevAboveVwap != null && prevAboveVwap && !aboveVwap;
        if (aboveVwap) {
            consecutiveAbove++;
            consecutiveBelow = 0;
        } else if (close.compareTo(vwapValue) < 0) {
            consecutiveBelow++;
            consecutiveAbove = 0;
        }
        prevAboveVwap = aboveVwap;

        boolean highProb = sd != null && sd.signum() > 0
                && close.subtract(vwapValue).abs().compareTo(cfg.highProbStdDev().multiply(sd)) >= 0;
        String probTag = highProb ? " [high-probability ±" + cfg.highProbStdDev() + "σ zone]" : "";

        // ── Sub-strategy B: Breakout (checked first; fires on the Nth hold candle) ──
        int hold = cfg.breakoutHoldCandles();
        if (consecutiveAbove == hold) {
            BigDecimal target = vwapValue.add(vwapValue.subtract(dayLow));   // VWAP + (VWAP − day low)
            lastEvaluation = String.format("BUY_CE breakout: %d closes above VWAP %.2f", hold, vwapValue);
            log.info("[VWAP] BUY CE breakout — {} closes above VWAP {} | target={} stop(VWAP)={}",
                    hold, vwapValue, target, vwapValue);
            return Optional.of(signal(SignalType.BUY_CE, close,
                    "VWAP breakout up: " + hold + " closes above VWAP " + vwapValue + probTag,
                    vwapValue, target));
        }
        if (consecutiveBelow == hold) {
            BigDecimal target = vwapValue.subtract(dayHigh.subtract(vwapValue));  // VWAP − (day high − VWAP)
            lastEvaluation = String.format("BUY_PE breakout: %d closes below VWAP %.2f", hold, vwapValue);
            log.info("[VWAP] BUY PE breakout — {} closes below VWAP {} | target={} stop(VWAP)={}",
                    hold, vwapValue, target, vwapValue);
            return Optional.of(signal(SignalType.BUY_PE, close,
                    "VWAP breakout down: " + hold + " closes below VWAP " + vwapValue + probTag,
                    vwapValue, target));
        }

        // ── Sub-strategy A: Reversion (VWAP reclaim/loss with RSI gate) ──
        boolean haveBands = sd != null && sd.signum() > 0;
        if (haveBands && bullishCross && rsiVal.compareTo(cfg.reversionRsiBuyMin()) > 0) {
            BigDecimal target = vwap.upperBand(cfg.bandStdDev());
            BigDecimal stop = vwap.lowerBand(cfg.bandStdDev());
            lastEvaluation = String.format("BUY_CE reversion: VWAP reclaim @ %.2f, RSI %.1f", close, rsiVal);
            log.info("[VWAP] BUY CE reversion — reclaim VWAP {} RSI {} | target(+1σ)={} stop(−1σ)={}",
                    vwapValue, rsiVal, target, stop);
            return Optional.of(signal(SignalType.BUY_CE, close,
                    "VWAP reclaim: close " + close + " back above VWAP " + vwapValue
                            + ", RSI " + rsiVal + " > " + cfg.reversionRsiBuyMin() + probTag,
                    stop, target));
        }
        if (haveBands && bearishCross && rsiVal.compareTo(cfg.reversionRsiSellMax()) < 0) {
            BigDecimal target = vwap.lowerBand(cfg.bandStdDev());
            BigDecimal stop = vwap.upperBand(cfg.bandStdDev());
            lastEvaluation = String.format("BUY_PE reversion: VWAP loss @ %.2f, RSI %.1f", close, rsiVal);
            log.info("[VWAP] BUY PE reversion — lose VWAP {} RSI {} | target(−1σ)={} stop(+1σ)={}",
                    vwapValue, rsiVal, target, stop);
            return Optional.of(signal(SignalType.BUY_PE, close,
                    "VWAP loss: close " + close + " back below VWAP " + vwapValue
                            + ", RSI " + rsiVal + " < " + cfg.reversionRsiSellMax() + probTag,
                    stop, target));
        }

        lastEvaluation = String.format("No setup: close=%.2f VWAP=%.2f RSI=%.1f above=%d below=%d",
                close, vwapValue, rsiVal, consecutiveAbove, consecutiveBelow);
        return Optional.empty();
    }

    private TradeSignal signal(SignalType type, BigDecimal trigger, String reason,
                               BigDecimal indexStopLoss, BigDecimal indexTarget) {
        return new TradeSignal(ID, type, underlying(), trigger, snapshot(), reason,
                clock.now().toInstant(), props.strategy().vwapStrategy().strikeOffset(),
                indexStopLoss, indexTarget);
    }

    @Override
    public IndicatorSnapshot snapshot() {
        return new IndicatorSnapshot(
                null, null, vwap.value(),   // vwap populated
                null, null, null,
                null, null,
                null, null,
                vwap.upperBand(BigDecimal.ONE), null, vwap.lowerBand(BigDecimal.ONE), rsi.value(), null);
    }

    @Override
    public Health health() {
        boolean ready = vwap.value() != null && rsi.isReady();
        BigDecimal vwapValue = vwap.value();
        return new Health(
                /*firstCandleCaptured=*/ rsi.isReady(),
                /*candlesProcessed=*/   candlesProcessed,
                /*indicatorsReady=*/    ready,
                /*emaBullish=*/         false,
                /*emaBearish=*/         false,
                /*aboveVwap=*/          ready && Boolean.TRUE.equals(prevAboveVwap),
                /*belowVwap=*/          ready && Boolean.FALSE.equals(prevAboveVwap),
                /*atrPass=*/            ready && vwapValue != null && vwap.stdDev() != null,
                /*lastEvaluation=*/     lastEvaluation);
    }

    @Override
    public void reset() {
        vwap.reset();
        rsi.reset();
        prevAboveVwap = null;
        consecutiveAbove = 0;
        consecutiveBelow = 0;
        dayHigh = null;
        dayLow = null;
        candlesProcessed = 0;
        lastEvaluation = "Reset for new trading day";
    }
}
