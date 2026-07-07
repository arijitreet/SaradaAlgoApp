/**
 * Dashboard layout persistence (v2 — react-grid-layout).
 *
 * Saved shape: array of {i, x, y, w, h} grid items (12-column grid,
 * rowHeight 40px, 20px margins). On load the saved layout is reconciled
 * against the section registry: items for sections that no longer exist are
 * dropped, sections added since the save are appended at the bottom, and
 * per-section min/max constraints are re-applied (they are code-defined and
 * never persisted). A v1 layout (order-only array of ids from the dnd-kit
 * implementation) or any invalid payload falls back to defaults gracefully.
 */

export interface PanelLayout {
  i: DashboardSectionId;
  x: number;
  y: number;
  w: number;
  h: number;
  minW?: number;
  minH?: number;
  maxH?: number;
}

export const DASHBOARD_SECTIONS = [
  "pnl-hero",
  "active-trade",
  "strategy-comparison",
  "strategy-selector",
  "market-overview",
  "strategy-health",
  "indicators",
  "activity-feed",
] as const;

export type DashboardSectionId = (typeof DASHBOARD_SECTIONS)[number];

/** Sizing constraints per section — floors chosen so content stays usable
 *  (scrolls within the panel rather than clipping). Never persisted. */
const CONSTRAINTS: Record<DashboardSectionId, Pick<PanelLayout, "minW" | "minH" | "maxH">> = {
  "pnl-hero": { minW: 4, minH: 3 },
  "active-trade": { minW: 3, minH: 4 },
  "strategy-comparison": { minW: 6, minH: 3 },
  "strategy-selector": { minW: 4, minH: 1, maxH: 2 },
  "market-overview": { minW: 5, minH: 4 },
  "strategy-health": { minW: 3, minH: 4 },
  indicators: { minW: 4, minH: 3 },
  "activity-feed": { minW: 3, minH: 3 },
};

/** Default 12-col arrangement: three equal cards across the top, strategies
 *  left with activity + indicators stacked right, market overview full-width. */
const DEFAULT_POSITIONS: Record<DashboardSectionId, { x: number; y: number; w: number; h: number }> = {
  "pnl-hero":            { x: 0, y:  0, w:  4, h: 5 },
  "active-trade":        { x: 4, y:  0, w:  4, h: 5 },
  "strategy-health":     { x: 8, y:  0, w:  4, h: 5 },
  "strategy-comparison": { x: 0, y:  5, w:  6, h: 9 },
  "activity-feed":       { x: 6, y:  5, w:  6, h: 4 },
  indicators:            { x: 6, y:  9, w:  6, h: 5 },
  "market-overview":     { x: 0, y: 14, w: 12, h: 9 },
  "strategy-selector":   { x: 0, y: 23, w: 12, h: 1 },
};

export function defaultLayout(): PanelLayout[] {
  return DASHBOARD_SECTIONS.map((i) => ({ i, ...DEFAULT_POSITIONS[i], ...CONSTRAINTS[i] }));
}

const STORAGE_KEY = "dashboard-layout-v4";
const LEGACY_KEYS = ["dashboard-layout-v1", "dashboard-layout-v2", "dashboard-layout-v3"];

function isValidItem(v: unknown): v is { i: string; x: number; y: number; w: number; h: number } {
  if (typeof v !== "object" || v === null) return false;
  const o = v as Record<string, unknown>;
  return (
    typeof o.i === "string" &&
    [o.x, o.y, o.w, o.h].every((n) => typeof n === "number" && Number.isFinite(n))
  );
}

export function loadLayout(): PanelLayout[] {
  // Old order-only layouts can't express sizes — drop them and start fresh.
  for (const key of LEGACY_KEYS) {
    try {
      localStorage.removeItem(key);
    } catch {
      // ignore
    }
  }
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return defaultLayout();
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return defaultLayout();

    const known = new Set<string>(DASHBOARD_SECTIONS);
    const seen = new Set<string>();
    const items: PanelLayout[] = [];
    for (const v of parsed) {
      if (!isValidItem(v) || !known.has(v.i) || seen.has(v.i)) continue;
      seen.add(v.i);
      const id = v.i as DashboardSectionId;
      const c = CONSTRAINTS[id];
      items.push({
        i: id,
        x: Math.max(0, Math.round(v.x)),
        y: Math.max(0, Math.round(v.y)),
        w: Math.max(c.minW ?? 1, Math.round(v.w)),
        h: Math.min(c.maxH ?? Infinity, Math.max(c.minH ?? 1, Math.round(v.h))),
        ...c,
      });
    }
    // Sections introduced after this layout was saved: append below everything.
    let bottom = items.reduce((m, it) => Math.max(m, it.y + it.h), 0);
    for (const id of DASHBOARD_SECTIONS) {
      if (seen.has(id)) continue;
      const d = DEFAULT_POSITIONS[id];
      items.push({ i: id, x: d.x, y: bottom, w: d.w, h: d.h, ...CONSTRAINTS[id] });
      bottom += d.h;
    }
    return items;
  } catch {
    return defaultLayout();
  }
}

let saveTimer: ReturnType<typeof setTimeout> | undefined;

/** Debounced write — coalesces the burst of layout-change events a single
 *  drag/resize produces into one storage write. Persists geometry only. */
export function saveLayout(layout: PanelLayout[]) {
  clearTimeout(saveTimer);
  const slim = layout.map(({ i, x, y, w, h }) => ({ i, x, y, w, h }));
  saveTimer = setTimeout(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(slim));
    } catch {
      // storage full/unavailable — layout just won't persist this session
    }
  }, 300);
}

export function clearLayout() {
  clearTimeout(saveTimer);
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    // ignore
  }
}
