import { motion, AnimatePresence } from "framer-motion";
import {
  Activity,
  ArrowDownRight,
  ArrowUpRight,
  Radio,
  ShieldAlert,
  Zap,
} from "lucide-react";
import { fmtTime } from "@/lib/format";
import { useLiveStore } from "@/stores/liveStore";
import { GlassCard, CardHeader } from "@/components/ui/card";

const KIND_ICON: Record<string, { icon: typeof Zap; className: string }> = {
  SIGNAL: { icon: Zap, className: "text-amber-300 bg-amber-400/10 border-amber-400/20" },
  ORDER: { icon: ArrowUpRight, className: "text-accent-glow bg-accent/10 border-accent/25" },
  POSITION: { icon: ArrowDownRight, className: "text-profit bg-profit/10 border-profit/25" },
  SESSION: { icon: Activity, className: "text-slate-300 bg-white/[0.05] border-white/10" },
  CONNECTION: { icon: Radio, className: "text-sky-300 bg-sky-400/10 border-sky-400/20" },
  RISK: { icon: ShieldAlert, className: "text-loss bg-loss/10 border-loss/25" },
};

/** Real-time event timeline: signals, orders, exits, session + feed changes. */
export function ActivityFeed() {
  const feed = useLiveStore((s) => s.feed);

  return (
    <GlassCard className="flex max-h-[420px] flex-col">
      <CardHeader title="Activity" subtitle="Live event stream" />
      <div className="-mr-2 flex-1 space-y-1 overflow-y-auto pr-2">
        {feed.length === 0 && (
          <motion.div
            animate={{ opacity: [0.4, 0.8, 0.4] }}
            transition={{ duration: 2.5, repeat: Infinity, ease: "easeInOut" }}
            className="py-10 text-center text-sm text-slate-600"
          >
            Events will appear here in real time
          </motion.div>
        )}
        <AnimatePresence initial={false}>
          {feed.map((entry, i) => {
            const meta = KIND_ICON[entry.kind] ?? KIND_ICON.SESSION;
            const Icon = meta.icon;
            return (
              <motion.div
                key={`${entry.at}-${i}`}
                layout
                initial={{ opacity: 0, x: -16, scale: 0.97 }}
                animate={{ opacity: 1, x: 0, scale: 1 }}
                exit={{ opacity: 0, x: 16, scale: 0.97 }}
                transition={{ type: "spring", stiffness: 360, damping: 30 }}
                whileHover={{ x: 2 }}
                className="flex items-start gap-3 rounded-xl px-2 py-2 transition-colors hover:bg-white/[0.03]"
              >
                <motion.span
                  initial={{ scale: 0.5, rotate: -20 }}
                  animate={{ scale: 1, rotate: 0 }}
                  transition={{ type: "spring", stiffness: 420, damping: 16 }}
                  className={`mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg border ${meta.className}`}
                >
                  <Icon size={13} />
                </motion.span>
                <div className="min-w-0 flex-1">
                  <div className="flex items-baseline justify-between gap-2">
                    <span className="truncate text-[13px] font-semibold text-slate-200">
                      {entry.title}
                    </span>
                    <span className="shrink-0 font-mono text-[10px] text-slate-600">
                      {fmtTime(entry.at)}
                    </span>
                  </div>
                  <div className="truncate text-xs text-slate-500">{entry.detail}</div>
                </div>
              </motion.div>
            );
          })}
        </AnimatePresence>
      </div>
    </GlassCard>
  );
}
