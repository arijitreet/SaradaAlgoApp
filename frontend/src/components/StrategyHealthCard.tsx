import { AnimatePresence, motion } from "framer-motion";
import { CheckCircle2, XCircle, CircleDashed } from "lucide-react";
import { cn } from "@/lib/utils";
import { useLiveStore } from "@/stores/liveStore";
import { GlassCard, CardHeader } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

const listContainer = {
  hidden: {},
  show: { transition: { staggerChildren: 0.06, delayChildren: 0.1 } },
};
const listItem = {
  hidden: { opacity: 0, x: -10 },
  show: { opacity: 1, x: 0, transition: { type: "spring", stiffness: 320, damping: 26 } },
};

interface Check {
  label: string;
  ok?: boolean | null;
}

function CheckList({ checks }: { checks: Check[] }) {
  return (
    <motion.ul initial="hidden" animate="show" variants={listContainer} className="space-y-2.5">
      {checks.map((check) => (
        <motion.li key={check.label} variants={listItem} className="flex items-center gap-2.5 text-sm">
          <AnimatePresence mode="wait" initial={false}>
            {check.ok == null ? (
              <motion.span
                key="dash"
                initial={{ opacity: 0, scale: 0.6 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0, scale: 0.6 }}
                transition={{ type: "spring", stiffness: 400, damping: 20 }}
                className="flex shrink-0"
              >
                <CircleDashed size={15} className="text-slate-600" />
              </motion.span>
            ) : check.ok ? (
              <motion.span
                key="ok"
                initial={{ opacity: 0, scale: 0.5, rotate: -45 }}
                animate={{ opacity: 1, scale: 1, rotate: 0 }}
                exit={{ opacity: 0, scale: 0.6 }}
                transition={{ type: "spring", stiffness: 400, damping: 18 }}
                className="flex shrink-0"
              >
                <CheckCircle2 size={15} className="text-profit" />
              </motion.span>
            ) : (
              <motion.span
                key="no"
                initial={{ opacity: 0, scale: 0.6 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0, scale: 0.6 }}
                transition={{ type: "spring", stiffness: 400, damping: 20 }}
                className="flex shrink-0"
              >
                <XCircle size={15} className="text-slate-500" />
              </motion.span>
            )}
          </AnimatePresence>
          <span className={cn("text-slate-400 transition-colors duration-300", check.ok && "text-slate-200")}>
            {check.label}
          </span>
        </motion.li>
      ))}
    </motion.ul>
  );
}

const isSupertrend = (id?: string) => id === "supertrend-flip-v1";

/** Rule-by-rule view of why the strategy is (or isn't) ready to fire — for both CE and PE setups. */
export function StrategyHealthCard() {
  const view = useLiveStore((s) => s.indicators);
  const health = view?.health;
  const st = isSupertrend(view?.strategyId);

  const sharedChecks: Check[] = st
    ? [
        { label: "Supertrend initialised", ok: health?.firstCandleCaptured },
        { label: "ATR(10) warmed up", ok: health?.indicatorsReady },
      ]
    : [
        { label: "First candle captured (09:15–09:20)", ok: health?.firstCandleCaptured },
        { label: "Indicators warmed up", ok: health?.indicatorsReady },
        { label: "ATR filter passing", ok: health?.atrPass },
      ];

  const ceChecks: Check[] = st
    ? [{ label: "Trend UP (bullish)", ok: health?.emaBullish }]
    : [
        { label: "EMA 9 > EMA 15", ok: health?.emaBullish },
        { label: "Price above VWAP", ok: health?.aboveVwap },
      ];

  const peChecks: Check[] = st
    ? [{ label: "Trend DOWN (bearish)", ok: health?.emaBearish }]
    : [
        { label: "EMA 9 < EMA 15", ok: health?.emaBearish },
        { label: "Price below VWAP", ok: health?.belowVwap },
      ];

  const subtitle = st ? "supertrend-flip-v1" : (view?.strategyId ?? "first-candle-breakout-v1");

  return (
    <GlassCard>
      <CardHeader
        title="Strategy Health"
        subtitle={subtitle}
        right={
          <Badge variant={health?.indicatorsReady ? "success" : "warning"} pulse={!health?.indicatorsReady}>
            {health ? `${health.candlesProcessed} candles` : "warming up"}
          </Badge>
        }
      />
      <CheckList checks={sharedChecks} />
      <div className="mt-4 grid grid-cols-2 gap-x-4 gap-y-2.5 border-t border-white/[0.06] pt-4">
        <div>
          <div className="stat-label mb-2.5 text-profit/80">CE setup</div>
          <CheckList checks={ceChecks} />
        </div>
        <div>
          <div className="stat-label mb-2.5 text-loss/80">PE setup</div>
          <CheckList checks={peChecks} />
        </div>
      </div>
      {health?.lastEvaluation && (
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.3, type: "spring", stiffness: 300, damping: 26 }}
          className="mt-4 rounded-xl border border-white/[0.06] bg-white/[0.02] p-3 font-mono text-[11px] leading-relaxed text-slate-500"
        >
          {health.lastEvaluation}
        </motion.div>
      )}
    </GlassCard>
  );
}
