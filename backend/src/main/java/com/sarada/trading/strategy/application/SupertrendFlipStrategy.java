package com.sarada.trading.strategy.application;

import com.sarada.trading.common.config.AppProperties;
import com.sarada.trading.common.events.DomainEvents;
import com.sarada.trading.common.market.Candle;
import com.sarada.trading.common.market.IndicatorSnapshot;
import com.sarada.trading.common.market.SignalType;
import com.sarada.trading.common.market.TradeSignal;
import com.sarada.trading.common.time.TradingClock;
import com.sarada.trading.strategy.domain.TradingStrategy;
import com.sarada.trading.strategy.domain.indicators.Supertrend;
import com.sarada.trading.strategy.domain.indicators.Vwap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Supertrend Flip strategy (5-minute Nifty candles).
 *
 *  Entry rules
 *  ───────────
 *  • Bullish flip: candle N−1 was BELOW the Supertrend line (bearish trend)
 *                  and candle N closes ABOVE it AND close is ABOVE VWAP → BUY CE.
 *  • Bearish flip: candle N−1 was ABOVE the Supertrend line (bullish trend)
 *                  and candle N closes BELOW it AND close is BELOW VWAP → BUY PE.
 *
 *  The VWAP confirmation (resets daily at 09:15 via {@link #reset()}) filters
 *  flips that fight the day's volume-weighted bias. A flip whose VWAP side does
 *  not confirm is skipped and won't re-fire (direction is consumed on the flip).
 *
 *  Guards
 *  ──────
 *  • No signal until ATR is fully seeded (atrPeriod candles).
 *  • First ready candle initialises direction without firing — avoids a false
 *    flip on the warm-up boundary.
 *  • Duplicate-candle guard: previousBullish is updated every candle, so a
 *    direction that doesn't change never re-fires the same signal.
 *
 *  Config is sourced from {@link SupertrendConfigService} and can be changed at
 *  runtime; an ATR/multiplier change rebuilds the indicator and restarts warm-up
 *  (see {@link #onConfigChanged}). All mutable state is guarded by the instance
 *  monitor since onCandle runs on the candle thread while reconfigure/reads arrive
 *  on HTTP threads.
 */
@Slf4j
@Component
public class SupertrendFlipStrategy implements TradingStrategy {

    public static final String ID = "supertrend-flip-v1";

    private final AppProperties props;
    private final TradingClock clock;
    private final SupertrendConfigService configService;

    private Supertrend supertrend;
    private final Vwap vwap = new Vwap();
    private Boolean previousBullish;   // null = not yet initialised after warm-up
    private int candlesProcessed;
    private String lastEvaluation = "Warming up";

    public SupertrendFlipStrategy(AppProperties props, TradingClock clock,
                                  SupertrendConfigService configService) {
        this.props = props;
        this.clock = clock;
        this.configService = configService;
        var cfg = configService.current();
        this.supertrend = new Supertrend(cfg.atrPeriod(), cfg.multiplier());
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
    public synchronized Optional<TradeSignal> onCandle(Candle candle) {
        // Capture direction BEFORE updating so we can detect the flip on this candle.
        Boolean prevBullish = previousBullish;

        supertrend.update(candle);
        vwap.update(candle);
        candlesProcessed++;

        if (!supertrend.isReady()) {
            lastEvaluation = "Warming up ATR(" + configService.current().atrPeriod() + "): "
                    + candlesProcessed + " candles";
            return Optional.empty();
        }

        boolean currentBullish = supertrend.isBullish();

        // First candle where ST is ready: initialise direction, no signal.
        if (prevBullish == null) {
            previousBullish = currentBullish;
            lastEvaluation = "Initialised: " + trendLabel(currentBullish)
                    + " — ST=" + supertrend.value();
            log.info("[ST] Supertrend initialised: {} ST={} close={}",
                    trendLabel(currentBullish), supertrend.value(), candle.close());
            return Optional.empty();
        }

        previousBullish = currentBullish;

        BigDecimal close = candle.close();
        BigDecimal vwapValue = vwap.value();
        boolean aboveVwap = vwapValue != null && close.compareTo(vwapValue) > 0;
        boolean belowVwap = vwapValue != null && close.compareTo(vwapValue) < 0;

        boolean bullishFlip = !prevBullish && currentBullish;
        boolean bearishFlip = prevBullish && !currentBullish;

        if (bullishFlip) {
            if (!aboveVwap) {
                lastEvaluation = String.format("Bullish flip skipped — close %.2f not above VWAP %.2f",
                        close, vwapValue);
                log.info("[ST] Bullish flip skipped (close {} ≤ VWAP {})", close, vwapValue);
                return Optional.empty();
            }
            lastEvaluation = String.format(
                    "Bullish flip ↑  close=%.2f ST=%.2f VWAP=%.2f",
                    close, supertrend.value(), vwapValue);
            log.info("[ST] BULLISH FLIP — BUY CE signal @ {} | ST={} VWAP={}",
                    close, supertrend.value(), vwapValue);
            return Optional.of(signal(SignalType.BUY_CE, close,
                    "Supertrend bullish flip: close " + close
                            + " crossed above ST=" + supertrend.value() + " and above VWAP=" + vwapValue));
        }

        if (bearishFlip) {
            if (!belowVwap) {
                lastEvaluation = String.format("Bearish flip skipped — close %.2f not below VWAP %.2f",
                        close, vwapValue);
                log.info("[ST] Bearish flip skipped (close {} ≥ VWAP {})", close, vwapValue);
                return Optional.empty();
            }
            lastEvaluation = String.format(
                    "Bearish flip ↓  close=%.2f ST=%.2f VWAP=%.2f",
                    close, supertrend.value(), vwapValue);
            log.info("[ST] BEARISH FLIP — BUY PE signal @ {} | ST={} VWAP={}",
                    close, supertrend.value(), vwapValue);
            return Optional.of(signal(SignalType.BUY_PE, close,
                    "Supertrend bearish flip: close " + close
                            + " crossed below ST=" + supertrend.value() + " and below VWAP=" + vwapValue));
        }

        lastEvaluation = String.format("No flip: %s  close=%.2f  ST=%.2f",
                trendLabel(currentBullish), candle.close(), supertrend.value());
        return Optional.empty();
    }

    /** Rebuild the indicator and restart warm-up when ATR period / multiplier change. */
    @EventListener
    public synchronized void onConfigChanged(DomainEvents.StrategyConfigChanged e) {
        if (!ID.equals(e.strategyId()) || !e.indicatorChanged()) {
            return;  // not us, or only strikeOffset changed (read live at signal time)
        }
        var cfg = configService.current();
        this.supertrend = new Supertrend(cfg.atrPeriod(), cfg.multiplier());
        this.previousBullish = null;
        this.candlesProcessed = 0;
        this.lastEvaluation = "Reconfigured — warming up ATR(" + cfg.atrPeriod()
                + "), mult " + cfg.multiplier();
        log.warn("[ST] reconfigured ATR={} mult={} — indicator rebuilt, warm-up restarted",
                cfg.atrPeriod(), cfg.multiplier());
    }

    private TradeSignal signal(SignalType type, BigDecimal trigger, String reason) {
        return new TradeSignal(ID, type, underlying(), trigger, snapshot(), reason,
                clock.now().toInstant(),
                configService.current().strikeOffset(), null, null);
    }

    @Override
    public synchronized IndicatorSnapshot snapshot() {
        return new IndicatorSnapshot(
                null, null, vwap.value(),       // emaFast, emaSlow — N/A; vwap now populated
                null, null, null,               // atr, support, resistance — N/A
                null, null,                     // firstCandleHigh, firstCandleLow — N/A
                supertrend.value(),             // supertrendLine
                supertrend.isReady() ? supertrend.isBullish() : null,   // supertrendBullish
                null, null, null, null, null);  // bb / rsi / adx — N/A
    }

    @Override
    public synchronized Health health() {
        boolean ready = supertrend.isReady();
        boolean bull = ready && supertrend.isBullish();
        BigDecimal vwapValue = vwap.value();
        return new Health(
                /*firstCandleCaptured=*/ ready,            // reused: "ST initialised"
                /*candlesProcessed=*/   candlesProcessed,
                /*indicatorsReady=*/    ready,
                /*emaBullish=*/         bull,              // reused: "Trend UP"
                /*emaBearish=*/         ready && !bull,    // reused: "Trend DOWN"
                /*aboveVwap=*/          vwapValue != null && bull,
                /*belowVwap=*/          vwapValue != null && ready && !bull,
                /*atrPass=*/            ready,             // reused: "ATR seeded"
                /*lastEvaluation=*/     lastEvaluation);
    }

    @Override
    public synchronized void reset() {
        var cfg = configService.current();
        this.supertrend = new Supertrend(cfg.atrPeriod(), cfg.multiplier());
        vwap.reset();
        previousBullish = null;
        candlesProcessed = 0;
        lastEvaluation = "Reset for new trading day";
    }

    private static String trendLabel(boolean bullish) {
        return bullish ? "BULLISH" : "BEARISH";
    }
}
