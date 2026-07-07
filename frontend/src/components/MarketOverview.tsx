import { useMemo } from "react";
import { motion } from "framer-motion";
import {
  Area,
  AreaChart,
  CartesianGrid,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { useCandles } from "@/hooks/queries";
import { useLiveStore } from "@/stores/liveStore";
import { fmtNum, fmtTime } from "@/lib/format";
import { GlassCard, CardHeader } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { AnimatedNumber } from "@/components/ui/animated-number";

/** Underlying price chart (5-min closes) with VWAP and first-candle levels. */
export function MarketOverview() {
  const { data: candles } = useCandles(100);
  const market = useLiveStore((s) => s.market);
  const indicators = useLiveStore((s) => s.indicators)?.indicators;

  const data = useMemo(() => {
    const base = (candles ?? []).map((c) => ({
      t: fmtTime(c.openTime),
      close: c.close,
    }));
    if (market && base.length > 0) {
      base.push({ t: "now", close: market.ltp });
    }
    return base;
  }, [candles, market]);

  return (
    <GlassCard className="col-span-full flex flex-col xl:col-span-2">
      <CardHeader
        title="Market Overview"
        subtitle="NIFTY 50 · 5-minute closes"
        right={
          market && (
            <Badge variant={market.dayChange >= 0 ? "success" : "danger"}>
              LTP <AnimatedNumber value={market.ltp} formatter={fmtNum} />
            </Badge>
          )
        }
      />
      {/* flex-1 so the chart grows/shrinks with the resizable dashboard panel;
          min-h keeps it readable at the panel's minimum size. */}
      <div className="min-h-[200px] flex-1">
        {data.length === 0 ? (
          <div className="relative flex h-full items-center justify-center overflow-hidden text-sm text-slate-600">
            <motion.div
              animate={{ x: ["-10%", "110%"] }}
              transition={{ duration: 2.5, repeat: Infinity, ease: "easeInOut" }}
              className="pointer-events-none absolute inset-y-0 w-24 bg-gradient-to-r from-transparent via-white/[0.05] to-transparent"
            />
            No candles yet — start the feed to populate the chart
          </div>
        ) : (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.5 }}
            className="h-full"
          >
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
              <defs>
                <linearGradient id="priceFill" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#6366f1" stopOpacity={0.35} />
                  <stop offset="100%" stopColor="#6366f1" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid stroke="rgba(255,255,255,0.04)" vertical={false} />
              <XAxis
                dataKey="t"
                tick={{ fill: "#475569", fontSize: 10 }}
                axisLine={false}
                tickLine={false}
                minTickGap={48}
              />
              <YAxis
                domain={["auto", "auto"]}
                tick={{ fill: "#475569", fontSize: 10 }}
                axisLine={false}
                tickLine={false}
                width={64}
                tickFormatter={(v: number) => v.toFixed(0)}
              />
              <Tooltip
                contentStyle={{
                  background: "rgba(11,14,23,0.92)",
                  border: "1px solid rgba(255,255,255,0.1)",
                  borderRadius: 12,
                  fontSize: 12,
                }}
                labelStyle={{ color: "#94a3b8" }}
              />
              {indicators?.vwap && (
                <ReferenceLine
                  y={indicators.vwap}
                  stroke="#f59e0b"
                  strokeDasharray="4 4"
                  label={{ value: "VWAP", fill: "#f59e0b", fontSize: 10, position: "right" }}
                />
              )}
              {indicators?.firstCandleHigh && (
                <ReferenceLine
                  y={indicators.firstCandleHigh}
                  stroke="#10b981"
                  strokeDasharray="2 4"
                  label={{ value: "FC HIGH", fill: "#10b981", fontSize: 9, position: "right" }}
                />
              )}
              {indicators?.firstCandleLow && (
                <ReferenceLine
                  y={indicators.firstCandleLow}
                  stroke="#f43f5e"
                  strokeDasharray="2 4"
                  label={{ value: "FC LOW", fill: "#f43f5e", fontSize: 9, position: "right" }}
                />
              )}
              <Area
                type="monotone"
                dataKey="close"
                stroke="#818cf8"
                strokeWidth={2}
                fill="url(#priceFill)"
                isAnimationActive={false}
                dot={false}
              />
            </AreaChart>
          </ResponsiveContainer>
          </motion.div>
        )}
      </div>
    </GlassCard>
  );
}
