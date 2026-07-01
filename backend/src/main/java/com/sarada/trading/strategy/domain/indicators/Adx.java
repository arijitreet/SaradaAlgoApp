package com.sarada.trading.strategy.domain.indicators;

import com.sarada.trading.common.market.Candle;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Wilder's Average Directional Index (ADX), incremental.
 *
 *   +DM = (high − prevHigh)  when that move is up and exceeds the down move, else 0
 *   −DM = (prevLow − low)    when that move is down and exceeds the up move, else 0
 *   TR  = true range
 *
 *   TR, +DM, −DM are Wilder-smoothed over `period`.
 *   +DI = 100 × smoothed+DM / smoothedTR
 *   −DI = 100 × smoothed−DM / smoothedTR
 *   DX  = 100 × |+DI − −DI| / (+DI + −DI)
 *   ADX = Wilder-smoothed DX (first value = average of the first `period` DX readings)
 *
 * A high ADX means a strong trend; a low ADX (< 25) means a ranging market.
 */
public class Adx {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final int period;

    private BigDecimal prevHigh;
    private BigDecimal prevLow;
    private BigDecimal prevClose;

    private BigDecimal trSum = BigDecimal.ZERO;
    private BigDecimal plusDmSum = BigDecimal.ZERO;
    private BigDecimal minusDmSum = BigDecimal.ZERO;
    private BigDecimal smTr;
    private BigDecimal smPlusDm;
    private BigDecimal smMinusDm;

    private BigDecimal dxSum = BigDecimal.ZERO;
    private BigDecimal adx;
    private int dmCount;
    private int dxCount;

    public Adx(int period) {
        this.period = period;
    }

    public void update(Candle candle) {
        if (prevClose == null) {
            prevHigh = candle.high();
            prevLow = candle.low();
            prevClose = candle.close();
            return;
        }

        BigDecimal upMove = candle.high().subtract(prevHigh);
        BigDecimal downMove = prevLow.subtract(candle.low());
        BigDecimal plusDm = (upMove.signum() > 0 && upMove.compareTo(downMove) > 0) ? upMove : BigDecimal.ZERO;
        BigDecimal minusDm = (downMove.signum() > 0 && downMove.compareTo(upMove) > 0) ? downMove : BigDecimal.ZERO;
        BigDecimal tr = trueRange(candle);

        prevHigh = candle.high();
        prevLow = candle.low();
        prevClose = candle.close();
        dmCount++;

        if (dmCount <= period) {
            trSum = trSum.add(tr);
            plusDmSum = plusDmSum.add(plusDm);
            minusDmSum = minusDmSum.add(minusDm);
            if (dmCount == period) {
                smTr = trSum;
                smPlusDm = plusDmSum;
                smMinusDm = minusDmSum;
                accumulateDx();
            }
            return;
        }

        // Wilder smoothing of TR / +DM / −DM.
        smTr = smTr.subtract(smTr.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP)).add(tr);
        smPlusDm = smPlusDm.subtract(smPlusDm.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP)).add(plusDm);
        smMinusDm = smMinusDm.subtract(smMinusDm.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP)).add(minusDm);
        accumulateDx();
    }

    private void accumulateDx() {
        BigDecimal dx = dx();
        dxCount++;
        if (dxCount <= period) {
            dxSum = dxSum.add(dx);
            if (dxCount == period) {
                adx = dxSum.divide(BigDecimal.valueOf(period), 2, RoundingMode.HALF_UP);
            }
            return;
        }
        adx = adx.multiply(BigDecimal.valueOf(period - 1L)).add(dx)
                .divide(BigDecimal.valueOf(period), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal dx() {
        if (smTr.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal plusDi = HUNDRED.multiply(smPlusDm).divide(smTr, MathContext.DECIMAL64);
        BigDecimal minusDi = HUNDRED.multiply(smMinusDm).divide(smTr, MathContext.DECIMAL64);
        BigDecimal diSum = plusDi.add(minusDi);
        if (diSum.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return HUNDRED.multiply(plusDi.subtract(minusDi).abs())
                .divide(diSum, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal trueRange(Candle candle) {
        BigDecimal highLow = candle.high().subtract(candle.low());
        BigDecimal highClose = candle.high().subtract(prevClose).abs();
        BigDecimal lowClose = candle.low().subtract(prevClose).abs();
        return highLow.max(highClose).max(lowClose);
    }

    /** Current ADX (0–100); null until fully seeded (~2 × period bars). */
    public BigDecimal value() {
        return adx;
    }

    public boolean isReady() {
        return adx != null;
    }

    public void reset() {
        prevHigh = null;
        prevLow = null;
        prevClose = null;
        trSum = BigDecimal.ZERO;
        plusDmSum = BigDecimal.ZERO;
        minusDmSum = BigDecimal.ZERO;
        smTr = null;
        smPlusDm = null;
        smMinusDm = null;
        dxSum = BigDecimal.ZERO;
        adx = null;
        dmCount = 0;
        dxCount = 0;
    }
}
