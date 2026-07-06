import { type ReactNode } from "react";
import { motion } from "framer-motion";
import { Zap } from "lucide-react";
import { cn } from "@/lib/utils";
import { fmtInrPrecise, fmtNum, fmtTime, pnlColor } from "@/lib/format";
import { useStrategyPerformance } from "@/hooks/queries";
import { GlassCard, CardHeader } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { AnimatedNumber } from "@/components/ui/animated-number";
import type { StrategyPerformanceView } from "@/types";

const LABELS: Record<string, string> = {
  "first-candle-breakout-v1": "First Candle Breakout",
  "supertrend-flip-v1": "Supertrend Flip",
  "multi-confluence-trend-v1": "Multi-Confluence Trend",
  "mean-reversion-v1": "Mean Reversion",
};

const signedInr = (v: number) => `${v >= 0 ? "+" : ""}${fmtInrPrecise(v)}`;

/** Both strategies side by side: live status, today's P&L, trades, position, last signal. */
export function StrategyComparison() {
  const { data } = useStrategyPerformance();
  const strategies = data ?? [];
  if (strategies.length === 0) return null;

  return (
    <GlassCard>
      <CardHeader title="Strategies" subtitle="live status &amp; today's P&L" />
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {strategies.map((s) => (
          <StrategyCard key={s.strategyId} s={s} />
        ))}
      </div>
    </GlassCard>
  );
}

function StrategyCard({ s }: { s: StrategyPerformanceView }) {
  // Sign-tinted left edge so each strategy's day result reads at a glance.
  const tint =
    s.totalPnl > 0
      ? "rgba(16,185,129,0.45)"
      : s.totalPnl < 0
        ? "rgba(244,63,94,0.45)"
        : "rgba(255,255,255,0.10)";

  return (
    <motion.div
      whileHover={{ y: -2 }}
      transition={{ type: "spring", stiffness: 320, damping: 24 }}
      style={{ borderLeftColor: tint, borderLeftWidth: 2 }}
      className="rounded-xl border border-white/[0.06] bg-white/[0.03] p-4 transition-colors hover:border-white/[0.12]"
    >
      <div className="flex items-start justify-between gap-2">
        <div>
          <div className="text-sm font-semibold text-slate-100">
            {LABELS[s.strategyId] ?? s.strategyId}
          </div>
          <div className="mt-0.5 font-mono text-[11px] text-slate-600">{s.strategyId}</div>
        </div>
        <Badge variant={s.active ? "success" : "neutral"} pulse={s.active}>
          {s.active ? (
            <>
              <Zap size={11} /> Active
            </>
          ) : (
            "Inactive"
          )}
        </Badge>
      </div>

      <div className="mt-3 flex items-baseline gap-2">
        <AnimatedNumber
          value={s.totalPnl}
          formatter={signedInr}
          className={cn("text-xl font-extrabold", pnlColor(s.totalPnl))}
        />
        <span className="text-[11px] text-slate-500">today</span>
      </div>

      <div className="mt-3 space-y-2 border-t border-white/[0.05] pt-3 text-xs">
        <Row label="Trades">
          <span className="num text-slate-200">
            {s.trades} / {s.maxTrades}
          </span>
        </Row>
        <Row label="Position">
          {s.openPosition ? (
            <span className={s.openPosition.optionType === "CE" ? "text-profit" : "text-loss"}>
              {fmtNum(s.openPosition.strike)} {s.openPosition.optionType} ·{" "}
              {signedInr(s.openPosition.unrealizedPnl)}
            </span>
          ) : (
            <span className="text-slate-600">Flat</span>
          )}
        </Row>
        <Row label="Last signal">
          {s.lastSignal ? (
            <span className="text-slate-300">
              {s.lastSignal.type} · {fmtTime(s.lastSignal.at)}
            </span>
          ) : (
            <span className="text-slate-600">—</span>
          )}
        </Row>
        <Row label="Window">
          <span className={cn("num", s.windowActive ? "text-profit" : "text-slate-500")}>
            {s.activeWindow}
            {s.windowActive && <span className="ml-1 text-[10px] uppercase tracking-wide">live</span>}
          </span>
        </Row>
      </div>
    </motion.div>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <span className="text-slate-500">{label}</span>
      {children}
    </div>
  );
}
