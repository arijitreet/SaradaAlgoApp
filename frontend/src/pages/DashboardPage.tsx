import { lazy, Suspense } from "react";
import { PnlHeroCard } from "@/components/PnlHeroCard";
import { ActiveTradePanel } from "@/components/ActiveTradePanel";
import { IndicatorDashboard } from "@/components/IndicatorDashboard";
import { StrategyHealthCard } from "@/components/StrategyHealthCard";
import { StrategySelector } from "@/components/StrategySelector";
import { StrategyComparison } from "@/components/StrategyComparison";
import { ActivityFeed } from "@/components/ActivityFeed";
import { MarketOverviewSkeleton } from "@/components/MarketOverviewSkeleton";

// Lazy so the heavy recharts chunk loads after the dashboard's first paint,
// not on the critical path. Named export → map to default for React.lazy.
const MarketOverview = lazy(() =>
  import("@/components/MarketOverview").then((m) => ({ default: m.MarketOverview }))
);

export default function DashboardPage() {
  return (
    <div className="grid grid-cols-1 gap-5 xl:grid-cols-3">
      {/* row 1 — hero + active trade */}
      <div className="xl:col-span-2">
        <PnlHeroCard />
      </div>
      <ActiveTradePanel />

      {/* row 2 — strategy comparison (both strategies side by side) */}
      <div className="xl:col-span-3">
        <StrategyComparison />
      </div>

      {/* row 3 — strategy selector (spans full width, only visible with >1 strategy) */}
      <div className="xl:col-span-3 flex items-center gap-2">
        <span className="stat-label text-slate-600">Strategy</span>
        <StrategySelector />
      </div>

      {/* row 4 — chart + strategy health */}
      <Suspense fallback={<MarketOverviewSkeleton />}>
        <MarketOverview />
      </Suspense>
      <StrategyHealthCard />

      {/* row 4 — indicators + activity */}
      <div className="xl:col-span-2">
        <IndicatorDashboard />
      </div>
      <ActivityFeed />
    </div>
  );
}
