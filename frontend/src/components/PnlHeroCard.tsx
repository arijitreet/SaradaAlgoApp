import { type CSSProperties } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { IndianRupee, Target, Zap } from "lucide-react";
import { cn } from "@/lib/utils";
import { fmtInrPrecise, pnlColor } from "@/lib/format";
import { useLiveStore } from "@/stores/liveStore";
import { useSettings } from "@/hooks/queries";
import { useUiStore } from "@/stores/uiStore";
import { Badge } from "@/components/ui/badge";
import { AnimatedNumber } from "@/components/ui/animated-number";
import { RollingNumber } from "@/components/ui/RollingNumber";
import { trackSpotlight } from "@/components/ui/card";

const signedInr = (v: number) => `${v >= 0 ? "+" : ""}${fmtInrPrecise(v)}`;

/** Hero card: live day P&L with realized / unrealized split and trade budget. */
export function PnlHeroCard() {
  const pnl = useLiveStore((s) => s.pnl);
  const position = useLiveStore((s) => s.position);
  const calm = useUiStore((s) => s.calmMode);
  const { data: settings } = useSettings();
  // Global daily cap (shared across all strategies); resets at 09:15 each day.
  const maxTrades = (settings?.trading as Record<string, unknown>)?.maxTradesPerDay as number ?? 6;
  const tradesUsed = pnl?.trades ?? 0;
  const tradesLeft = Math.max(0, maxTrades - tradesUsed);

  const total = pnl?.total ?? 0;
  const positive = total >= 0;
  const glow = calm ? "transparent" : positive ? "rgba(16,185,129,0.45)" : "rgba(244,63,94,0.45)";
  const edge = positive ? "rgba(16,185,129,0.5)" : "rgba(244,63,94,0.5)";

  return (
    <motion.div
      initial={{ opacity: 0, y: 18, scale: 0.98 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      whileHover={{ y: -4 }}
      transition={{ type: "spring", stiffness: 260, damping: 26 }}
      onMouseMove={trackSpotlight}
      style={{ ["--pnl-glow"]: glow, ["--glass-edge"]: edge } as CSSProperties}
      className={cn(
        "glass glass-hover relative overflow-hidden p-6 transition-shadow duration-700",
        positive ? "shadow-glow-profit-lg" : "shadow-glow-loss-lg"
      )}
    >
      {/* ambient gradient halo, slowly rotating, tracking pnl sign */}
      <motion.div
        animate={{ rotate: 360 }}
        transition={{ duration: 50, repeat: Infinity, ease: "linear" }}
        className={cn(
          "pointer-events-none absolute -right-32 -top-32 h-80 w-80 rounded-full opacity-25 blur-3xl transition-colors duration-700",
          positive
            ? "bg-gradient-to-br from-profit via-emerald-400/80 to-transparent"
            : "bg-gradient-to-br from-loss via-rose-400/80 to-transparent"
        )}
      />
      <div
        className={cn(
          "orb -bottom-20 -left-16 h-56 w-56 opacity-[0.12] animate-blob transition-colors duration-700",
          positive ? "bg-profit" : "bg-loss"
        )}
      />

      <div className="relative flex items-start justify-between">
        <div>
          <div className="stat-label flex items-center gap-1.5">
            <motion.span
              animate={{ rotate: [0, -12, 12, 0] }}
              transition={{ duration: 4, repeat: Infinity, ease: "easeInOut" }}
            >
              <IndianRupee size={11} />
            </motion.span>
            Today's P&L
          </div>
          <RollingNumber
            value={total}
            formatter={signedInr}
            className={cn(
              "mt-2 block text-5xl font-extrabold tracking-tight [text-shadow:0_0_28px_var(--pnl-glow)]",
              pnlColor(total)
            )}
          />
        </div>

        <AnimatePresence mode="wait">
          <motion.div
            key={position ? "in" : "flat"}
            initial={{ opacity: 0, scale: 0.8, y: -6 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.8, y: 6 }}
            transition={{ type: "spring", stiffness: 400, damping: 22 }}
          >
            <Badge variant={position ? "accent" : "neutral"} pulse={!!position}>
              <Zap size={11} /> {position ? "IN POSITION" : "FLAT"}
            </Badge>
          </motion.div>
        </AnimatePresence>
      </div>

      <div className="relative mt-6 grid grid-cols-3 gap-4 border-t border-white/[0.06] pt-4">
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.15, type: "spring", stiffness: 280, damping: 24 }}
        >
          <div className="stat-label">Realized</div>
          <AnimatedNumber
            value={pnl?.realized ?? 0}
            formatter={fmtInrPrecise}
            className={cn("mt-1 block text-base font-bold", pnlColor(pnl?.realized))}
          />
        </motion.div>
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.25, type: "spring", stiffness: 280, damping: 24 }}
        >
          <div className="stat-label">Unrealized</div>
          <AnimatedNumber
            value={pnl?.unrealized ?? 0}
            formatter={fmtInrPrecise}
            className={cn("mt-1 block text-base font-bold", pnlColor(pnl?.unrealized))}
          />
        </motion.div>
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.35, type: "spring", stiffness: 280, damping: 24 }}
        >
          <div className="stat-label flex items-center gap-1">
            <Target size={11} /> Trades
          </div>
          <div className="num mt-1 text-base font-bold text-slate-200">
            {tradesUsed}
            <span className="text-slate-500"> / {maxTrades}</span>
          </div>
          <div className="mt-0.5 text-[10px] text-slate-500">{tradesLeft} left today</div>
        </motion.div>
      </div>
    </motion.div>
  );
}
