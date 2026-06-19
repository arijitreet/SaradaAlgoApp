package com.sarada.trading.strategy.domain.indicators;

import com.sarada.trading.common.market.Candle;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Supertrend indicator using Wilder's ATR smoothing.
 *
 * Algorithm (standard TradingView / Chartink convention):
 *   HL2   = (High + Low) / 2
 *   Basic Upper Band (BUB) = HL2 + factor × ATR
 *   Basic Lower Band (BLB) = HL2 − factor × ATR
 *
 *   Final Upper Band (FUB): ratchets downward only; resets when prev close > FUB
 *   Final Lower Band (FLB): ratchets upward only; resets when prev close < FLB
 *
 *   Trend direction: starts from first bar, flips when close crosses the active band.
 *   When bullish → ST line = FLB (trailing support)
 *   When bearish → ST line = FUB (overhead resistance)
 *
 * isReady() returns true only after the ATR period is fully seeded (Wilder-smooth).
 */
public class Supertrend {

    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private final Atr atr;
    private final BigDecimal factor;

    private BigDecimal finalUpperBand;
    private BigDecimal finalLowerBand;
    private BigDecimal supertrendValue;
    private boolean bullish;
    private BigDecimal prevClose;

    public Supertrend(int atrPeriod, BigDecimal factor) {
        this.atr = new Atr(atrPeriod);
        this.factor = factor;
    }

    public void update(Candle candle) {
        atr.update(candle);

        BigDecimal hl2 = candle.high().add(candle.low())
                .divide(TWO, 4, RoundingMode.HALF_UP);
        BigDecimal atrVal = atr.value();

        BigDecimal basicUpper = hl2.add(factor.multiply(atrVal));
        BigDecimal basicLower = hl2.subtract(factor.multiply(atrVal));

        if (finalUpperBand == null) {
            // Bootstrap: first bar initialises both bands and sets initial trend.
            finalUpperBand = basicUpper;
            finalLowerBand = basicLower;
            bullish = candle.close().compareTo(basicUpper) > 0;
            supertrendValue = bullish ? finalLowerBand : finalUpperBand;
            prevClose = candle.close();
            return;
        }

        // Final Upper Band only moves down (or resets when prev close broke above it).
        if (basicUpper.compareTo(finalUpperBand) < 0
                || prevClose.compareTo(finalUpperBand) > 0) {
            finalUpperBand = basicUpper;
        }

        // Final Lower Band only moves up (or resets when prev close broke below it).
        if (basicLower.compareTo(finalLowerBand) > 0
                || prevClose.compareTo(finalLowerBand) < 0) {
            finalLowerBand = basicLower;
        }

        // Trend continuation / flip.
        if (!bullish) {
            bullish = candle.close().compareTo(finalUpperBand) > 0;
        } else {
            bullish = !(candle.close().compareTo(finalLowerBand) < 0);
        }

        supertrendValue = bullish ? finalLowerBand : finalUpperBand;
        prevClose = candle.close();
    }

    /** Current Supertrend line value (lower band when bullish, upper band when bearish). */
    public BigDecimal value() {
        return supertrendValue;
    }

    /** True when the last closed candle sits above the Supertrend line (bullish trend). */
    public boolean isBullish() {
        return bullish;
    }

    /**
     * True once the underlying ATR has completed its full seeding period.
     * Signals should only be considered after this returns true.
     */
    public boolean isReady() {
        return atr.isReady() && supertrendValue != null;
    }

    public void reset() {
        atr.reset();
        finalUpperBand = null;
        finalLowerBand = null;
        supertrendValue = null;
        prevClose = null;
    }
}
