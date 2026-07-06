import { motion } from "framer-motion";
import { cn } from "@/lib/utils";
import { fmtNum } from "@/lib/format";
import { useLiveStore } from "@/stores/liveStore";
import { GlassCard, CardHeader } from "@/components/ui/card";
import type { IndicatorSnapshot, MarketUpdate } from "@/types";

type Tone = "profit" | "loss" | undefined;
type Cell = { label: string; value: string; sub?: string; tone?: Tone };

/** Live indicator values driving the currently selected strategy. */
export function IndicatorDashboard() {
  const view = useLiveStore((s) => s.indicators);
  const ind = view?.indicators;
  const market = useLiveStore((s) => s.market);
  const id = view?.strategyId;

  const { cells, subtitle } = cellsFor(id, ind, market);

  return (
    <GlassCard>
      <CardHeader title="Indicators" subtitle={subtitle} />
      <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-4">
        {cells.map((cell, i) => (
          <motion.div
            key={cell.label}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            whileHover={{ y: -3, scale: 1.02 }}
            transition={{ delay: i * 0.04, type: "spring", stiffness: 320, damping: 22 }}
            className="rounded-xl border border-white/[0.06] bg-white/[0.03] px-3 py-2.5 transition-colors duration-300 hover:border-white/[0.14] hover:bg-white/[0.045]"
          >
            <div className="stat-label">{cell.label}</div>
            <div
              className={cn(
                "num mt-1 text-sm font-bold text-slate-100",
                cell.tone === "profit" && "text-profit",
                cell.tone === "loss" && "text-loss"
              )}
            >
              {cell.value}
            </div>
            {cell.sub && <div className="mt-0.5 text-[10px] text-slate-600">{cell.sub}</div>}
          </motion.div>
        ))}
      </div>
    </GlassCard>
  );
}

function cellsFor(
  id: string | undefined,
  ind: IndicatorSnapshot | undefined,
  market: MarketUpdate | null
): { cells: Cell[]; subtitle: string } {
  if (id === "supertrend-flip-v1") {
    return { cells: supertrendCells(ind, market), subtitle: "5-minute · Supertrend + VWAP" };
  }
  if (id === "multi-confluence-trend-v1") {
    return { cells: multiConfluenceCells(ind), subtitle: "5-minute · EMA + Supertrend + 15m" };
  }
  if (id === "mean-reversion-v1") {
    return { cells: meanReversionCells(ind), subtitle: "5-minute · BB + RSI + ADX" };
  }
  return { cells: fcbCells(ind, market), subtitle: "5-minute · live" };
}

function fcbCells(ind: IndicatorSnapshot | undefined, market: MarketUpdate | null): Cell[] {
  const emaTone: Tone =
    ind?.emaFast != null && ind?.emaSlow != null
      ? ind.emaFast > ind.emaSlow
        ? "profit"
        : ind.emaFast < ind.emaSlow
          ? "loss"
          : undefined
      : undefined;
  const vsVwap =
    ind?.vwap != null && market != null ? (market.ltp > ind.vwap ? "above" : "below") : null;
  return [
    { label: "EMA 9", value: fmtNum(ind?.emaFast), tone: emaTone },
    { label: "EMA 15", value: fmtNum(ind?.emaSlow) },
    {
      label: "VWAP",
      value: fmtNum(ind?.vwap),
      sub: vsVwap ? `price ${vsVwap}` : undefined,
      tone: vsVwap === "above" ? "profit" : vsVwap === "below" ? "loss" : undefined,
    },
    { label: "ATR 14", value: fmtNum(ind?.atr), sub: "volatility" },
    { label: "Support", value: fmtNum(ind?.support), tone: "profit" },
    { label: "Resistance", value: fmtNum(ind?.resistance), tone: "loss" },
    { label: "FC High", value: fmtNum(ind?.firstCandleHigh), sub: "breakout ↑" },
    { label: "FC Low", value: fmtNum(ind?.firstCandleLow), sub: "breakdown ↓" },
  ];
}

function supertrendCells(ind: IndicatorSnapshot | undefined, market: MarketUpdate | null): Cell[] {
  const bull = ind?.supertrendBullish;
  const tone: Tone = bull == null ? undefined : bull ? "profit" : "loss";
  const vsVwap =
    ind?.vwap != null && market != null ? (market.ltp > ind.vwap ? "above" : "below") : null;
  return [
    {
      label: "Supertrend",
      value: fmtNum(ind?.supertrendLine),
      sub: bull == null ? "warming up" : bull ? "bullish ↑" : "bearish ↓",
      tone,
    },
    { label: "Trend", value: bull == null ? "—" : bull ? "UP" : "DOWN", tone },
    {
      label: "VWAP",
      value: fmtNum(ind?.vwap),
      sub: vsVwap ? `price ${vsVwap}` : "gate",
      tone: vsVwap === "above" ? "profit" : vsVwap === "below" ? "loss" : undefined,
    },
    {
      label: "LTP vs ST",
      value:
        ind?.supertrendLine != null && market != null
          ? fmtNum(market.ltp - ind.supertrendLine)
          : "—",
      sub: "distance",
    },
  ];
}

function multiConfluenceCells(ind: IndicatorSnapshot | undefined): Cell[] {
  const emaTone: Tone =
    ind?.emaFast != null && ind?.emaSlow != null
      ? ind.emaFast > ind.emaSlow
        ? "profit"
        : "loss"
      : undefined;
  const bull = ind?.supertrendBullish;
  const stTone: Tone = bull == null ? undefined : bull ? "profit" : "loss";
  return [
    { label: "EMA 9", value: fmtNum(ind?.emaFast), tone: emaTone },
    { label: "EMA 21", value: fmtNum(ind?.emaSlow), sub: "5-min cross" },
    {
      label: "Supertrend",
      value: fmtNum(ind?.supertrendLine),
      sub: bull == null ? "warming up" : bull ? "bullish ↑" : "bearish ↓",
      tone: stTone,
    },
    { label: "Trend", value: bull == null ? "—" : bull ? "UP" : "DOWN", tone: stTone },
  ];
}

function meanReversionCells(ind: IndicatorSnapshot | undefined): Cell[] {
  const rsi = ind?.rsi;
  const rsiTone: Tone = rsi == null ? undefined : rsi < 30 ? "profit" : rsi > 70 ? "loss" : undefined;
  const adx = ind?.adx;
  const adxTone: Tone = adx == null ? undefined : adx < 25 ? "profit" : "loss";
  return [
    { label: "BB Upper", value: fmtNum(ind?.bbUpper), sub: "sell zone", tone: "loss" },
    { label: "BB Mid", value: fmtNum(ind?.bbMiddle), sub: "20 SMA · target" },
    { label: "BB Lower", value: fmtNum(ind?.bbLower), sub: "buy zone", tone: "profit" },
    {
      label: "RSI 14",
      value: fmtNum(rsi),
      sub: rsi == null ? undefined : rsi < 30 ? "oversold" : rsi > 70 ? "overbought" : "neutral",
      tone: rsiTone,
    },
    {
      label: "ADX 14",
      value: fmtNum(adx),
      sub: adx == null ? undefined : adx < 25 ? "ranging ✓" : "trending ✗",
      tone: adxTone,
    },
  ];
}
