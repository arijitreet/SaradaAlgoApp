import { useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { cn } from "@/lib/utils";
import { fmtDate, fmtInrPrecise, fmtNum, fmtTime, pnlColor } from "@/lib/format";
import { useAuditLog, useTradeHistory } from "@/hooks/queries";
import { GlassCard, CardHeader } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

export default function HistoryPage() {
  const [tab, setTab] = useState<"trades" | "audit">("trades");

  return (
    <div className="space-y-5">
      <div className="flex items-center gap-2">
        {(["trades", "audit"] as const).map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={cn(
              "relative rounded-xl px-4 py-2 text-sm font-semibold capitalize transition-colors",
              tab === t ? "text-white" : "text-slate-500 hover:text-slate-300"
            )}
          >
            {tab === t && (
              <motion.span
                layoutId="history-tab"
                className="absolute inset-0 rounded-xl border border-white/10 bg-white/[0.08]"
                transition={{ type: "spring", stiffness: 400, damping: 32 }}
              />
            )}
            <span className="relative z-10">{t === "trades" ? "Trade history" : "Audit log"}</span>
          </button>
        ))}
      </div>
      <AnimatePresence mode="wait">
        <motion.div
          key={tab}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -8 }}
          transition={{ type: "spring", stiffness: 320, damping: 30 }}
        >
          {tab === "trades" ? <TradesTable /> : <AuditTable />}
        </motion.div>
      </AnimatePresence>
    </div>
  );
}

function TradesTable() {
  const [page, setPage] = useState(0);
  const { data, isLoading } = useTradeHistory(page);

  return (
    <GlassCard>
      <CardHeader title="Closed Trades" subtitle={`${data?.totalElements ?? 0} total`} />
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-white/[0.06] text-[11px] uppercase tracking-wider text-slate-500">
              <th className="pb-3 pr-4 font-medium">Day</th>
              <th className="pb-3 pr-4 font-medium">Contract</th>
              <th className="pb-3 pr-4 font-medium">Qty</th>
              <th className="pb-3 pr-4 font-medium">Entry</th>
              <th className="pb-3 pr-4 font-medium">Exit</th>
              <th className="pb-3 pr-4 font-medium">Reason</th>
              <th className="pb-3 pr-4 font-medium">Closed</th>
              <th className="pb-3 text-right font-medium">P&L</th>
            </tr>
          </thead>
          <tbody>
            {(data?.content ?? []).map((trade, i) => (
              <motion.tr
                key={trade.id}
                initial={{ opacity: 0, x: -8 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: i * 0.025, type: "spring", stiffness: 320, damping: 30 }}
                className="border-b border-white/[0.04] transition-colors last:border-0 hover:bg-white/[0.02]"
              >
                <td className="py-3 pr-4 text-slate-400">{fmtDate(trade.tradingDay)}</td>
                <td className="py-3 pr-4">
                  <span className="font-mono text-xs text-slate-200">{trade.tradingsymbol}</span>{" "}
                  <Badge variant={trade.optionType === "CE" ? "success" : "danger"}>
                    {trade.optionType}
                  </Badge>
                </td>
                <td className="num py-3 pr-4 text-slate-400">{trade.quantity}</td>
                <td className="num py-3 pr-4 text-slate-300">{fmtNum(trade.entryPrice)}</td>
                <td className="num py-3 pr-4 text-slate-300">{fmtNum(trade.exitPrice)}</td>
                <td className="py-3 pr-4">
                  <span className="text-xs text-slate-500">{trade.exitReason ?? "—"}</span>
                </td>
                <td className="py-3 pr-4 font-mono text-xs text-slate-500">
                  {fmtTime(trade.closedAt)}
                </td>
                <td className={cn("num py-3 text-right font-bold", pnlColor(trade.realizedPnl))}>
                  {trade.realizedPnl != null && trade.realizedPnl >= 0 ? "+" : ""}
                  {fmtInrPrecise(trade.realizedPnl)}
                </td>
              </motion.tr>
            ))}
            {!isLoading && (data?.content.length ?? 0) === 0 && (
              <tr>
                <td colSpan={8} className="py-10 text-center text-slate-600">
                  No closed trades yet
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager page={page} totalPages={data?.totalPages ?? 0} onChange={setPage} />
    </GlassCard>
  );
}

function AuditTable() {
  const [page, setPage] = useState(0);
  const { data } = useAuditLog(page);

  return (
    <GlassCard>
      <CardHeader title="Audit Log" subtitle="Every decision, order and state change" />
      <div className="space-y-1">
        {(data?.content ?? []).map((entry, i) => (
          <motion.div
            key={entry.id}
            initial={{ opacity: 0, x: -8 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ delay: i * 0.02, type: "spring", stiffness: 320, damping: 30 }}
            className="flex items-start gap-3 rounded-lg px-2 py-2 transition-colors hover:bg-white/[0.02]"
          >
            <Badge variant="neutral" className="mt-0.5 w-24 shrink-0 justify-center">
              {entry.category}
            </Badge>
            <div className="min-w-0 flex-1">
              <div className="text-[13px] font-semibold text-slate-200">{entry.action}</div>
              {entry.detail && (
                <div className="truncate text-xs text-slate-500">{entry.detail}</div>
              )}
            </div>
            <div className="shrink-0 text-right">
              <div className="font-mono text-[10px] text-slate-600">
                {fmtDate(entry.createdAt)} {fmtTime(entry.createdAt)}
              </div>
              <div className="text-[10px] text-slate-700">{entry.actor}</div>
            </div>
          </motion.div>
        ))}
      </div>
      <Pager page={page} totalPages={data?.totalPages ?? 0} onChange={setPage} />
    </GlassCard>
  );
}

function Pager({
  page,
  totalPages,
  onChange,
}: {
  page: number;
  totalPages: number;
  onChange: (p: number) => void;
}) {
  if (totalPages <= 1) return null;
  return (
    <div className="mt-4 flex items-center justify-end gap-2 border-t border-white/[0.06] pt-4">
      <span className="mr-2 text-xs text-slate-500">
        Page {page + 1} of {totalPages}
      </span>
      <Button variant="outline" size="sm" disabled={page === 0} onClick={() => onChange(page - 1)}>
        Prev
      </Button>
      <Button
        variant="outline"
        size="sm"
        disabled={page >= totalPages - 1}
        onClick={() => onChange(page + 1)}
      >
        Next
      </Button>
    </div>
  );
}
