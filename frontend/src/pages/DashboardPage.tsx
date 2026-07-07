import { lazy, Suspense, useEffect, useState, type ReactNode } from "react";
import { GridLayout, useContainerWidth, type Layout } from "react-grid-layout";
import "react-grid-layout/css/styles.css";
import { GripHorizontal, RotateCcw } from "lucide-react";
import { PnlHeroCard } from "@/components/PnlHeroCard";
import { ActiveTradePanel } from "@/components/ActiveTradePanel";
import { IndicatorDashboard } from "@/components/IndicatorDashboard";
import { StrategyHealthCard } from "@/components/StrategyHealthCard";
import { StrategySelector } from "@/components/StrategySelector";
import { StrategyComparison } from "@/components/StrategyComparison";
import { ActivityFeed } from "@/components/ActivityFeed";
import { MarketOverviewSkeleton } from "@/components/MarketOverviewSkeleton";
import { Button } from "@/components/ui/button";
import {
  defaultLayout,
  clearLayout,
  loadLayout,
  saveLayout,
  type DashboardSectionId,
  type PanelLayout,
} from "@/lib/dashboardLayout";

// Lazy so the heavy recharts chunk loads after the dashboard's first paint,
// not on the critical path. Named export → map to default for React.lazy.
const MarketOverview = lazy(() =>
  import("@/components/MarketOverview").then((m) => ({ default: m.MarketOverview }))
);

/**
 * Section registry: id → content. Geometry (x/y/w/h + min sizes) lives in the
 * layout objects from dashboardLayout.ts. Keys are stable, so react-grid-layout
 * repositions DOM nodes without remounting — live WS/query subscriptions inside
 * each section are untouched by drags and resizes.
 */
const SECTIONS: Record<DashboardSectionId, ReactNode> = {
  "pnl-hero": <PnlHeroCard />,
  "active-trade": <ActiveTradePanel />,
  "strategy-comparison": <StrategyComparison />,
  "strategy-selector": (
    <div className="flex items-center gap-2">
      <span className="stat-label text-slate-600">Strategy</span>
      <StrategySelector />
    </div>
  ),
  "market-overview": (
    <Suspense fallback={<MarketOverviewSkeleton />}>
      <MarketOverview />
    </Suspense>
  ),
  "strategy-health": <StrategyHealthCard />,
  indicators: <IndicatorDashboard />,
  "activity-feed": <ActivityFeed />,
};

/** Below xl the dashboard is a plain single-column stack (as before the grid):
 *  drag/resize are disabled there — panels auto-size to their content. */
const DESKTOP_QUERY = "(min-width: 1280px)";

function useIsDesktop() {
  const [desktop, setDesktop] = useState(() => window.matchMedia(DESKTOP_QUERY).matches);
  useEffect(() => {
    const mq = window.matchMedia(DESKTOP_QUERY);
    const onChange = (e: MediaQueryListEvent) => setDesktop(e.matches);
    mq.addEventListener("change", onChange);
    return () => mq.removeEventListener("change", onChange);
  }, []);
  return desktop;
}

export default function DashboardPage() {
  const [layout, setLayout] = useState<PanelLayout[]>(loadLayout);
  const desktop = useIsDesktop();

  const onLayoutChange = (next: Layout) => {
    // Merge geometry back into our items (keeps code-defined min/max intact).
    setLayout((prev) => {
      const byId = new Map(next.map((it) => [it.i, it]));
      const merged = prev.map((p) => {
        const n = byId.get(p.i);
        return n ? { ...p, x: n.x, y: n.y, w: n.w, h: n.h } : p;
      });
      saveLayout(merged);
      return merged;
    });
  };

  const resetLayout = () => {
    clearLayout();
    setLayout(defaultLayout());
  };

  // Mobile / narrow: stacked single column in saved visual order, no drag/resize.
  if (!desktop) {
    const ordered = [...layout].sort((a, b) => a.y - b.y || a.x - b.x);
    return (
      <div className="flex flex-col gap-5">
        {ordered.map((item) => (
          <div key={item.i} className="grid">
            {SECTIONS[item.i]}
          </div>
        ))}
      </div>
    );
  }

  return <DesktopGrid layout={layout} onLayoutChange={onLayoutChange} onReset={resetLayout} />;
}

/**
 * Own component so useContainerWidth (and its ResizeObserver) mounts and
 * unmounts together with the desktop branch — keeping the hook in the page
 * left the observer attached to a dead node after a mobile↔desktop switch,
 * freezing the measured width.
 */
function DesktopGrid({
  layout,
  onLayoutChange,
  onReset,
}: {
  layout: PanelLayout[];
  onLayoutChange: (next: Layout) => void;
  onReset: () => void;
}) {
  const { width, containerRef, mounted } = useContainerWidth();

  return (
    <div>
      <div className="mb-3 flex justify-end">
        <Button variant="ghost" size="sm" onClick={onReset}>
          <RotateCcw size={13} /> Reset layout
        </Button>
      </div>

      {/* cast: the hook types its ref for React 19 (`| null`); React 18's ref prop is stricter */}
      <div ref={containerRef as React.RefObject<HTMLDivElement>}>
        {mounted && (
          <GridLayout
            width={width}
            layout={layout}
            gridConfig={{ cols: 12, rowHeight: 40, margin: [20, 20], containerPadding: [0, 0] }}
            dragConfig={{ enabled: true, handle: ".drag-handle" }}
            resizeConfig={{ enabled: true, handles: ["se"] }}
            onLayoutChange={onLayoutChange}
          >
            {layout.map((item) => (
              <div key={item.i} className="group/panel relative">
                <button
                  type="button"
                  aria-label="Drag to rearrange section"
                  title="Drag to rearrange"
                  className="drag-handle absolute left-1/2 top-1.5 z-20 -translate-x-1/2 cursor-grab touch-none rounded-md border border-white/10 bg-base-950/80 px-2 py-0.5 text-slate-500 opacity-0 backdrop-blur transition-opacity duration-150 hover:text-slate-300 focus-visible:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/60 active:cursor-grabbing group-hover/panel:opacity-100"
                >
                  <GripHorizontal size={13} />
                </button>
                {/* Scroll shell: content scrolls inside the panel when smaller
                    than its natural size; the grid wrapper stretches the section
                    to fill the panel when larger. */}
                <div className="h-full overflow-auto">
                  <div className="grid min-h-full">{SECTIONS[item.i]}</div>
                </div>
              </div>
            ))}
          </GridLayout>
        )}
      </div>
    </div>
  );
}
