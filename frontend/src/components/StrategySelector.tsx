import { motion } from "framer-motion";
import { cn } from "@/lib/utils";
import { useLiveStore } from "@/stores/liveStore";

const STRATEGY_LABELS: Record<string, string> = {
  "first-candle-breakout-v1": "First Candle Breakout",
  "supertrend-flip-v1": "Supertrend Flip",
};

/**
 * Pill-tab row that lets the user switch which strategy's indicators and
 * health card are displayed on the dashboard. Appears only when more than
 * one strategy has sent data via WebSocket.
 */
export function StrategySelector() {
  const indicatorsMap = useLiveStore((s) => s.indicatorsMap);
  const selectedId = useLiveStore((s) => s.selectedStrategyId);
  const setSelected = useLiveStore((s) => s.setSelectedStrategy);

  const ids = Object.keys(indicatorsMap);
  if (ids.length <= 1) return null;

  return (
    <div className="flex items-center gap-2">
      {ids.map((id) => {
        const active = id === selectedId;
        return (
          <motion.button
            key={id}
            onClick={() => setSelected(id)}
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.97 }}
            className={cn(
              "rounded-full px-3 py-1 text-xs font-medium transition-colors duration-200",
              active
                ? "bg-white/[0.12] text-slate-100 shadow-inner"
                : "text-slate-500 hover:text-slate-300 hover:bg-white/[0.06]"
            )}
          >
            {STRATEGY_LABELS[id] ?? id}
          </motion.button>
        );
      })}
    </div>
  );
}
