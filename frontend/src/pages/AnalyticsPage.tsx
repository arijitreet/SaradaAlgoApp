import { useState } from "react";
import { motion } from "framer-motion";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { cn } from "@/lib/utils";
import { fmtDate, fmtInrPrecise, pnlColor } from "@/lib/format";
import { useAnalyticsSummary, useEquityCurve } from "@/hooks/queries";
import { GlassCard, CardHeader } from "@/components/ui/card";

type Period = "daily" | "weekly" | "monthly";

export default function AnalyticsPage() {
  const [period, setPeriod] = useState<Period>("daily");
  const { data: summary } = useAnalyticsSummary(period);
  const { data: equity } = useEquityCurve(30);

  const chartData = (equity ?? []).map((p) => ({
    day: fmtDate(p.day),
    dayPnl: p.dayPnl,
    cumulative: p.cumulativePnl,
  }));

  return (
    <div className="space-y-5">
      {/* period switch */}
      <div className="flex items-center gap-2">
        {(["daily", "weekly", "monthly"] as Period[]).map((p) => (
          <button
            key={p}
            onClick={() => setPeriod(p)}
            className={cn(
              "relative rounded-xl px-4 py-2 text-sm font-semibold capitalize transition-colors",
              period === p ? "text-white" : "text-slate-500 hover:text-slate-300"
            )}
          >
            {period === p && (
              <motion.span
                layoutId="analytics-period"
                className="absolute inset-0 rounded-xl border border-white/10 bg-white/[0.08]"
                transition={{ type: "spring", stiffness: 400, damping: 32 }}
              />
            )}
            <span className="relative z-10">{p}</span>
          </button>
        ))}
      </div>

      {/* summary stats */}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard index={0} label="Net P&L" value={fmtInrPrecise(summary?.grossPnl)} tone={summary?.grossPnl} />
        <StatCard
          index={1}
          label="Win rate"
          value={summary ? `${summary.winRatePct}%` : "—"}
          sub={summary ? `${summary.wins}W · ${summary.losses}L of ${summary.trades}` : undefined}
        />
        <StatCard index={2} label="Best trade" value={fmtInrPrecise(summary?.bestTrade)} tone={summary?.bestTrade} />
        <StatCard index={3} label="Avg / trade" value={fmtInrPrecise(summary?.avgPnlPerTrade)} tone={summary?.avgPnlPerTrade} />
      </div>

      {/* equity curve */}
      <GlassCard transition={{ delay: 0.1, type: "spring", stiffness: 280, damping: 28 }}>
        <CardHeader title="Equity Curve" subtitle="Cumulative realized P&L · last 30 days" />
        <div className="h-[260px]">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={chartData} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
              <CartesianGrid stroke="rgba(255,255,255,0.04)" vertical={false} />
              <XAxis dataKey="day" tick={{ fill: "#475569", fontSize: 10 }} axisLine={false} tickLine={false} />
              <YAxis tick={{ fill: "#475569", fontSize: 10 }} axisLine={false} tickLine={false} width={70} />
              <Tooltip
                contentStyle={{
                  background: "rgba(11,14,23,0.92)",
                  border: "1px solid rgba(255,255,255,0.1)",
                  borderRadius: 12,
                  fontSize: 12,
                }}
              />
              <Line
                type="monotone"
                dataKey="cumulative"
                stroke="#10b981"
                strokeWidth={2.5}
                dot={false}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </GlassCard>

      {/* daily bars */}
      <GlassCard transition={{ delay: 0.18, type: "spring", stiffness: 280, damping: 28 }}>
        <CardHeader title="Daily P&L" subtitle="Per-day realized result" />
        <div className="h-[220px]">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={chartData} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
              <CartesianGrid stroke="rgba(255,255,255,0.04)" vertical={false} />
              <XAxis dataKey="day" tick={{ fill: "#475569", fontSize: 10 }} axisLine={false} tickLine={false} />
              <YAxis tick={{ fill: "#475569", fontSize: 10 }} axisLine={false} tickLine={false} width={70} />
              <Tooltip
                cursor={{ fill: "rgba(255,255,255,0.03)" }}
                contentStyle={{
                  background: "rgba(11,14,23,0.92)",
                  border: "1px solid rgba(255,255,255,0.1)",
                  borderRadius: 12,
                  fontSize: 12,
                }}
              />
              <Bar dataKey="dayPnl" radius={[5, 5, 0, 0]}>
                {chartData.map((entry, i) => (
                  <Cell key={i} fill={entry.dayPnl >= 0 ? "#10b981" : "#f43f5e"} fillOpacity={0.85} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      </GlassCard>
    </div>
  );
}

function StatCard({
  index,
  label,
  value,
  sub,
  tone,
}: {
  index: number;
  label: string;
  value: string;
  sub?: string;
  tone?: number | null;
}) {
  return (
    <GlassCard className="p-4" transition={{ delay: index * 0.06, type: "spring", stiffness: 300, damping: 26 }}>
      <div className="stat-label">{label}</div>
      <div className={cn("num mt-1.5 text-xl font-extrabold text-slate-100", tone != null && pnlColor(tone))}>
        {value}
      </div>
      {sub && <div className="mt-1 text-[11px] text-slate-500">{sub}</div>}
    </GlassCard>
  );
}
