import { useMemo, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  RefreshCw,
  TrendingDown,
  AlertCircle,
  SearchX,
  ArrowUp,
  ArrowDown,
  ArrowUpDown,
} from "lucide-react";
import { useGapDownScanner, useRefreshGapDownScan } from "@/hooks/queries";
import { fmtNum, fmtDate } from "@/lib/format";
import { cn } from "@/lib/utils";
import { GlassCard, CardHeader } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import type { GapDownStock } from "@/types";

// ── Sorting ──
type SortColumn = "symbol" | "ltp" | "gapDown";
type SortDirection = "asc" | "desc" | "default";
interface SortConfig {
  column: SortColumn | null;
  direction: SortDirection;
}

/** Clickable column header that cycles asc → desc → default and shows the matching arrow. */
function SortableHeader({
  label,
  column,
  align,
  sortConfig,
  onSort,
}: {
  label: string;
  column: SortColumn;
  align: "left" | "right";
  sortConfig: SortConfig;
  onSort: (column: SortColumn) => void;
}) {
  const active = sortConfig.column === column;
  const Icon = !active
    ? ArrowUpDown
    : sortConfig.direction === "asc"
    ? ArrowUp
    : ArrowDown;

  return (
    <th
      onClick={() => onSort(column)}
      className={cn(
        "cursor-pointer select-none py-2.5 px-4 transition-colors hover:text-slate-300",
        align === "left" ? "text-left" : "text-right",
        active && "font-semibold text-accent-glow"
      )}
    >
      <span className="inline-flex items-center gap-1">
        {label}
        <Icon size={12} className={cn(!active && "opacity-40")} />
      </span>
    </th>
  );
}

function StockRow({ stock, index }: { stock: GapDownStock; index: number }) {
  return (
    <motion.tr
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: index * 0.04, type: "spring", stiffness: 300, damping: 28 }}
      className="border-b border-white/[0.04] last:border-0 hover:bg-white/[0.02] transition-colors"
    >
      <td className="py-3 px-4 text-right">
        <span className="num text-sm text-slate-500">{index + 1}</span>
      </td>
      <td className="py-3 px-4">
        <span className="text-sm font-semibold text-slate-100">{stock.symbol}</span>
      </td>
      <td className="py-3 px-4 text-right">
        <span className="num text-sm text-slate-200">{fmtNum(stock.ltp)}</span>
      </td>
      <td className="py-3 px-4 text-right">
        <span className="num text-sm text-slate-400">{fmtNum(stock.todayOpen)}</span>
      </td>
      <td className="py-3 px-4 text-right">
        <span className="num text-sm text-slate-400">{fmtNum(stock.fifteenthDayClose)}</span>
      </td>
      <td className="py-3 px-4 text-right">
        <span className="num text-sm font-semibold text-loss">
          <TrendingDown size={13} className="mr-1 inline-block" />
          {fmtNum(stock.gapDownPercent)}%
        </span>
      </td>
      <td className="py-3 px-4 text-right">
        <span className="num text-sm text-slate-500">{fmtNum(stock.fifteenDayHigh)}</span>
      </td>
      <td className="py-3 px-4 text-right">
        <span className="num text-sm text-slate-500">{fmtNum(stock.fifteenDayLow)}</span>
      </td>
    </motion.tr>
  );
}

export default function StocksPage() {
  const scan = useGapDownScanner();
  const refresh = useRefreshGapDownScan();

  const [sortConfig, setSortConfig] = useState<SortConfig>({
    column: null,
    direction: "default",
  });

  // Cycle the clicked column: new column → asc; same column → asc → desc → default.
  const handleSort = (column: SortColumn) => {
    setSortConfig((prev) => {
      if (prev.column !== column) {
        return { column, direction: "asc" };
      }
      if (prev.direction === "asc") return { column, direction: "desc" };
      if (prev.direction === "desc") return { column: null, direction: "default" };
      return { column, direction: "asc" };
    });
  };

  // Derived view over the cached scan results — no API re-call. "default" returns
  // the original scan order untouched (scan.stocks is the source-of-truth order).
  const sortedStocks = useMemo(() => {
    const { column, direction } = sortConfig;
    if (!column || direction === "default") return scan.stocks;

    const dir = direction === "asc" ? 1 : -1;
    return [...scan.stocks].sort((a, b) => {
      switch (column) {
        case "symbol":
          return a.symbol.localeCompare(b.symbol, undefined, { sensitivity: "base" }) * dir;
        case "ltp":
          return (a.ltp - b.ltp) * dir;
        case "gapDown":
          return (a.gapDownPercent - b.gapDownPercent) * dir;
        default:
          return 0;
      }
    });
  }, [scan.stocks, sortConfig]);

  return (
    <div className="space-y-5">
      <GlassCard>
        <CardHeader
          title="Gap-Down Scanner"
          subtitle="Nifty 200 · 1-day timeframe · stocks below 15-day lows with ≥5% gap"
          right={
            <div className="flex items-center gap-3">
              {scan.scanTime && (
                <span className="text-xs text-slate-500">
                  Scanned {fmtDate(scan.scanTime)} · {scan.scanned} stocks
                </span>
              )}
              {scan.scanning && (
                <Badge variant="neutral" pulse>
                  <RefreshCw size={12} className="animate-spin" />
                  Scanning…
                </Badge>
              )}
              <Button
                variant="ghost"
                size="sm"
                disabled={scan.scanning || refresh.isPending}
                onClick={() => refresh.mutate()}
              >
                <RefreshCw
                  size={14}
                  className={cn(scan.scanning && "animate-spin")}
                />
                Refresh
              </Button>
            </div>
          }
        />

        {scan.error && !scan.scanning && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="mb-4 flex items-center gap-2 rounded-lg border border-loss/20 bg-loss/5 px-4 py-2.5 text-sm text-loss"
          >
            <AlertCircle size={15} />
            {scan.error}
          </motion.div>
        )}

        <AnimatePresence mode="wait">
          {scan.scanning && scan.stocks.length === 0 ? (
            <motion.div
              key="loading"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="flex h-48 flex-col items-center justify-center gap-3"
            >
              <RefreshCw size={28} className="animate-spin text-accent-glow" />
              <p className="text-sm text-slate-500">
                Scanning Nifty 200 stocks… this takes about a minute
              </p>
            </motion.div>
          ) : scan.stocks.length === 0 && !scan.scanning ? (
            <motion.div
              key="empty"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="flex h-48 flex-col items-center justify-center gap-3"
            >
              <SearchX size={28} className="text-slate-600" />
              <p className="text-sm text-slate-500">
                {scan.scanTime
                  ? "No gap-down opportunities found today"
                  : "Click Refresh to run the first scan"}
              </p>
            </motion.div>
          ) : (
            <motion.div
              key="table"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="-mx-5 overflow-x-auto"
            >
              <table className="w-full min-w-[700px]">
                <thead>
                  <tr className="border-b border-white/[0.06] text-xs font-medium uppercase tracking-wider text-slate-500">
                    <th className="py-2.5 px-4 text-right">#</th>
                    <SortableHeader
                      label="Symbol"
                      column="symbol"
                      align="left"
                      sortConfig={sortConfig}
                      onSort={handleSort}
                    />
                    <SortableHeader
                      label="LTP"
                      column="ltp"
                      align="right"
                      sortConfig={sortConfig}
                      onSort={handleSort}
                    />
                    <th className="py-2.5 px-4 text-right">Today Open</th>
                    <th className="py-2.5 px-4 text-right">15th Day Close</th>
                    <SortableHeader
                      label="Gap Down %"
                      column="gapDown"
                      align="right"
                      sortConfig={sortConfig}
                      onSort={handleSort}
                    />
                    <th className="py-2.5 px-4 text-right">15D High</th>
                    <th className="py-2.5 px-4 text-right">15D Low</th>
                  </tr>
                </thead>
                <tbody>
                  {sortedStocks.map((stock, i) => (
                    <StockRow key={stock.symbol} stock={stock} index={i} />
                  ))}
                </tbody>
              </table>
            </motion.div>
          )}
        </AnimatePresence>
      </GlassCard>
    </div>
  );
}
