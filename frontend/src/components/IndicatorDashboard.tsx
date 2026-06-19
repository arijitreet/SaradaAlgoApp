import { motion } from "framer-motion";
import { cn } from "@/lib/utils";
import { fmtNum } from "@/lib/format";
import { useLiveStore } from "@/stores/liveStore";
import { GlassCard, CardHeader } from "@/components/ui/card";

const isSupertrend = (id?: string) => id === "supertrend-flip-v1";

/** Live indicator values driving the currently selected strategy. */
export function IndicatorDashboard() {
  const view = useLiveStore((s) => s.indicators);
  const ind = view?.indicators;
  const market = useLiveStore((s) => s.market);
  const st = isSupertrend(view?.strategyId);

  // ── First-Candle Breakout cells ──────────────────────────────────────────
  const emaTrend =
    ind?.emaFast != null && ind?.emaSlow != null
      ? ind.emaFast > ind.emaSlow
        ? "bull"
        : ind.emaFast < ind.emaSlow
          ? "bear"
          : "flat"
      : "flat";

  const vsVwap =
    ind?.vwap != null && market != null
      ? market.ltp > ind.vwap
        ? "above"
        : "below"
      : null;

  const fcbCells = [
    {
      label: "EMA 9",
      value: fmtNum(ind?.emaFast),
      tone: emaTrend === "bull" ? "profit" : emaTrend === "bear" ? "loss" : undefined,
    },
    { label: "EMA 15", value: fmtNum(ind?.emaSlow) },
    {
      label: "VWAP",
      value: fmtNum(ind?.vwap),
      sub: vsVwap ? `price ${vsVwap}` : undefined,
      tone: vsVwap === "above" ? "profit" : vsVwap === "below" ? "loss" : undefined,
    },
    { label: "ATR 14", value: fmtNum(ind?.atr), sub: "volatility" },
    { label: "Support", value: fmtNum(ind?.support), tone: "profit" as const },
    { label: "Resistance", value: fmtNum(ind?.resistance), tone: "loss" as const },
    { label: "FC High", value: fmtNum(ind?.firstCandleHigh), sub: "breakout ↑" },
    { label: "FC Low", value: fmtNum(ind?.firstCandleLow), sub: "breakdown ↓" },
  ];

  // ── Supertrend Flip cells ────────────────────────────────────────────────
  const stBull = ind?.supertrendBullish;
  const stTrend =
    stBull == null ? "flat" : stBull ? "bull" : "bear";

  const stCells = [
    {
      label: "Supertrend",
      value: fmtNum(ind?.supertrendLine),
      sub: stBull == null ? "warming up" : stBull ? "bullish ↑" : "bearish ↓",
      tone: stTrend === "bull" ? "profit" : stTrend === "bear" ? "loss" : undefined,
    },
    {
      label: "Trend",
      value: stBull == null ? "—" : stBull ? "UP" : "DOWN",
      tone: stTrend === "bull" ? "profit" : stTrend === "bear" ? "loss" : undefined,
    },
    {
      label: "LTP vs ST",
      value:
        ind?.supertrendLine != null && market != null
          ? fmtNum(market.ltp - ind.supertrendLine)
          : "—",
      sub: "distance",
      tone:
        ind?.supertrendLine != null && market != null
          ? market.ltp > ind.supertrendLine
            ? "profit"
            : "loss"
          : undefined,
    },
    { label: "ATR 10", value: fmtNum(ind?.atr), sub: "volatility" },
  ];

  const cells = st ? stCells : fcbCells;
  const subtitle = st ? "5-minute · Supertrend" : "5-minute · live";
  const gridClass = st
    ? "grid grid-cols-2 gap-2.5 sm:grid-cols-4"
    : "grid grid-cols-2 gap-2.5 sm:grid-cols-4";

  return (
    <GlassCard>
      <CardHeader title="Indicators" subtitle={subtitle} />
      <div className={gridClass}>
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
            {"sub" in cell && cell.sub && (
              <div className="mt-0.5 text-[10px] text-slate-600">{cell.sub}</div>
            )}
          </motion.div>
        ))}
      </div>
    </GlassCard>
  );
}
