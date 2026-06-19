import { motion } from "framer-motion";
import { GlassCard, CardHeader } from "@/components/ui/card";

/**
 * Lightweight placeholder shown while the lazily-loaded MarketOverview (and the
 * heavy recharts chunk it pulls) streams in. Matches the real card's footprint
 * exactly so the dashboard layout doesn't shift when the chart arrives.
 */
export function MarketOverviewSkeleton() {
  return (
    <GlassCard className="col-span-full xl:col-span-2">
      <CardHeader title="Market Overview" subtitle="NIFTY 50 · 5-minute closes" />
      <div className="relative h-[260px] overflow-hidden rounded-xl border border-white/[0.05] bg-white/[0.02]">
        <motion.div
          animate={{ x: ["-15%", "115%"] }}
          transition={{ duration: 1.6, repeat: Infinity, ease: "easeInOut" }}
          className="pointer-events-none absolute inset-y-0 w-32 bg-gradient-to-r from-transparent via-white/[0.06] to-transparent"
        />
        <div className="flex h-full items-center justify-center text-xs text-slate-600">
          Loading chart…
        </div>
      </div>
    </GlassCard>
  );
}
