package com.sarada.trading.strategy.application;

import com.sarada.trading.common.config.AppProperties;
import com.sarada.trading.common.market.Candle;
import com.sarada.trading.common.market.IndicatorSnapshot;
import com.sarada.trading.common.market.SignalType;
import com.sarada.trading.common.market.TradeSignal;
import com.sarada.trading.common.time.TradingClock;
import com.sarada.trading.strategy.domain.TradingStrategy;
import com.sarada.trading.strategy.domain.indicators.Ema;
import com.sarada.trading.strategy.domain.indicators.HigherTimeframeTrend;
import com.sarada.trading.strategy.domain.indicators.Supertrend;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Multi-Confluence Trend Following (5-minute Nifty candles).
 *
 *  Entry rules — all must align on the signal candle:
 *  ─────────────────────────────────────────────────
 *  • BUY CE: 9 EMA crosses ABOVE 21 EMA  AND  Supertrend is bullish
 *            AND the 15-min higher-timeframe trend is bullish (or not yet seeded).
 *  • BUY PE: 9 EMA crosses BELOW 21 EMA  AND  Supertrend is bearish
 *            AND the 15-min higher-timeframe trend is bearish (or not yet seeded).
 *
 *  Notes
 *  ─────
 *  • Volume confirmation is intentionally omitted: signals are computed on the
 *    NIFTY 50 index, which carries no traded volume.
 *  • The signal fires on the EMA *crossover* (one signal per cross), not on every
 *    candle the trend persists.
 *  • Higher-timeframe alignment is enforced only once the 15-min EMAs are seeded
 *    (~21×3 five-minute candles). Until then the 5-min confluence stands alone so
 *    the morning window is not dead while the HTF warms up.
 *  • Risk uses the default SL ladder (SL 25 / T2 50 = 1:2 R:R), satisfying the
 *    "target = 2× SL" requirement without touching SL logic.
 */
@Slf4j
@Component
public class MultiConfluenceTrendStrategy implements TradingStrategy {

    public static final String ID = "multi-confluence-trend-v1";

    private final AppProperties props;
    private final TradingClock clock;

    private final Ema emaFast;
    private final Ema emaSlow;
    private final Supertrend supertrend;
    private final HigherTimeframeTrend htf;

    private Boolean previousFastAboveSlow;   // null until both EMAs are first ready
    private int candlesProcessed;
    private String lastEvaluation = "Warming up";

    public MultiConfluenceTrendStrategy(AppProperties props, TradingClock clock) {
        this.props = props;
        this.clock = clock;
        var cfg = props.strategy().multiConfluenceTrend();
        this.emaFast = new Ema(cfg.emaFast());
        this.emaSlow = new Ema(cfg.emaSlow());
        this.supertrend = new Supertrend(cfg.atrPeriod(), cfg.multiplier());
        this.htf = new HigherTimeframeTrend(cfg.htfEmaFast(), cfg.htfEmaSlow());
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
        emaFast.update(candle.close());
        emaSlow.update(candle.close());
        supertrend.update(candle);
        htf.update(candle);
        candlesProcessed++;

        if (!emaFast.isReady() || !emaSlow.isReady() || !supertrend.isReady()) {
            lastEvaluation = "Warming up: " + candlesProcessed + " candles";
            return Optional.empty();
        }

        boolean fastAboveSlow = emaFast.value().compareTo(emaSlow.value()) > 0;

        // First ready candle: initialise the EMA relationship without firing.
        if (previousFastAboveSlow == null) {
            previousFastAboveSlow = fastAboveSlow;
            lastEvaluation = "Initialised: 9EMA " + (fastAboveSlow ? ">" : "≤") + " 21EMA";
            return Optional.empty();
        }

        boolean bullishCross = !previousFastAboveSlow && fastAboveSlow;
        boolean bearishCross = previousFastAboveSlow && !fastAboveSlow;
        previousFastAboveSlow = fastAboveSlow;

        boolean stBullish = supertrend.isBullish();
        Boolean htfBullish = htf.bullish();   // null = not yet seeded → do not block

        if (bullishCross) {
            if (!stBullish) {
                lastEvaluation = "Bullish cross skipped — Supertrend not bullish";
                return Optional.empty();
            }
            if (htfBullish != null && !htfBullish) {
                lastEvaluation = "Bullish cross skipped — 15m HTF trend bearish";
                return Optional.empty();
            }
            lastEvaluation = String.format("BUY_CE: 9EMA>21EMA, ST bullish, HTF=%s @ %.2f",
                    htfState(htfBullish), candle.close());
            log.info("[MCT] BUY CE — 9/21 bull cross + ST bullish + HTF {} @ {}",
                    htfState(htfBullish), candle.close());
            return Optional.of(signal(SignalType.BUY_CE, candle.close(),
                    "9EMA crossed above 21EMA; Supertrend bullish; 15m HTF " + htfState(htfBullish)));
        }

        if (bearishCross) {
            if (stBullish) {
                lastEvaluation = "Bearish cross skipped — Supertrend not bearish";
                return Optional.empty();
            }
            if (htfBullish != null && htfBullish) {
                lastEvaluation = "Bearish cross skipped — 15m HTF trend bullish";
                return Optional.empty();
            }
            lastEvaluation = String.format("BUY_PE: 9EMA<21EMA, ST bearish, HTF=%s @ %.2f",
                    htfState(htfBullish), candle.close());
            log.info("[MCT] BUY PE — 9/21 bear cross + ST bearish + HTF {} @ {}",
                    htfState(htfBullish), candle.close());
            return Optional.of(signal(SignalType.BUY_PE, candle.close(),
                    "9EMA crossed below 21EMA; Supertrend bearish; 15m HTF " + htfState(htfBullish)));
        }

        lastEvaluation = String.format("No cross: 9EMA %s 21EMA, ST=%s",
                fastAboveSlow ? ">" : "≤", stBullish ? "bull" : "bear");
        return Optional.empty();
    }

    private static String htfState(Boolean htfBullish) {
        return htfBullish == null ? "warming-up" : (htfBullish ? "bullish" : "bearish");
    }

    private TradeSignal signal(SignalType type, BigDecimal trigger, String reason) {
        return new TradeSignal(ID, type, underlying(), trigger, snapshot(), reason,
                clock.now().toInstant(), props.strategy().multiConfluenceTrend().strikeOffset(),
                null, null);
    }

    @Override
    public IndicatorSnapshot snapshot() {
        return new IndicatorSnapshot(
                emaFast.value(), emaSlow.value(), null,   // emaFast, emaSlow; vwap N/A
                null, null, null,                          // atr, support, resistance N/A
                null, null,                                // firstCandleHigh/Low N/A
                supertrend.value(),
                supertrend.isReady() ? supertrend.isBullish() : null,
                null, null, null, null, null);             // bb / rsi / adx — N/A
    }

    @Override
    public Health health() {
        boolean emasReady = emaFast.isReady() && emaSlow.isReady();
        boolean stReady = supertrend.isReady();
        boolean fastAboveSlow = emasReady && emaFast.value().compareTo(emaSlow.value()) > 0;
        Boolean htfBullish = htf.bullish();
        return new Health(
                /*firstCandleCaptured=*/ htf.isReady(),         // reused: "HTF ready"
                /*candlesProcessed=*/   candlesProcessed,
                /*indicatorsReady=*/    emasReady && stReady,
                /*emaBullish=*/         fastAboveSlow,
                /*emaBearish=*/         emasReady && !fastAboveSlow,
                /*aboveVwap=*/          Boolean.TRUE.equals(htfBullish),   // reused: "HTF bull"
                /*belowVwap=*/          Boolean.FALSE.equals(htfBullish),  // reused: "HTF bear"
                /*atrPass=*/            stReady,
                /*lastEvaluation=*/     lastEvaluation);
    }

    @Override
    public void reset() {
        emaFast.reset();
        emaSlow.reset();
        supertrend.reset();
        htf.reset();
        previousFastAboveSlow = null;
        candlesProcessed = 0;
        lastEvaluation = "Reset for new trading day";
    }
}
