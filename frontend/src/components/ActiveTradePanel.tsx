import { type CSSProperties } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Crosshair, Check, LogOut } from "lucide-react";
import { cn } from "@/lib/utils";
import { fmtDate, fmtNum, fmtInrPrecise, fmtTime, pnlColor } from "@/lib/format";
import { useLiveStore } from "@/stores/liveStore";
import { useActivePositions, useExitPosition } from "@/hooks/queries";
import { GlassCard, CardHeader } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { AnimatedNumber } from "@/components/ui/animated-number";
import type { PositionView } from "@/types";

const STAGE_STEPS = [
  { key: "INITIAL", label: "Initial" },
  { key: "BREAKEVEN", label: "Breakeven" },
  { key: "LOCKED", label: "Locked" },
  { key: "TRAILING", label: "Trailing" },
] as const;

const signedInr = (v: number) => `${v >= 0 ? "+" : ""}${fmtInrPrecise(v)}`;

export function ActiveTradePanel() {
  const livePositions = useLiveStore((s) => s.positions);
  // Seed from the DB-backed endpoint so open positions survive page loads /
  // app restarts; live WS updates take over (and win on conflict) as soon as ticks flow.
  const { data: activePositions } = useActivePositions();
  const exit = useExitPosition();

  const byId = new Map<number, PositionView>();
  for (const p of activePositions ?? []) byId.set(p.id, p);
  for (const p of Object.values(livePositions)) byId.set(p.id, p);
  const positions = [...byId.values()].sort((a, b) => a.id - b.id);

  const worstPnl = positions.length > 0 ? Math.min(...positions.map((p) => p.unrealizedPnl)) : 0;
  const edge =
    positions.length === 0
      ? "rgba(99,102,241,0.22)"
      : worstPnl >= 0
        ? "rgba(16,185,129,0.5)"
        : "rgba(244,63,94,0.5)";

  return (
    <GlassCard
      className="flex min-h-[280px] flex-col"
      style={{ ["--glass-edge"]: edge } as CSSProperties}
      transition={{ delay: 0.05, type: "spring", stiffness: 300, damping: 28 }}
    >
      <CardHeader
        title={positions.length > 1 ? "Active Trades" : "Active Trade"}
        subtitle="Tick-level stop management"
        right={
          positions.length > 0 && (
            <Badge variant="accent">
              {positions.length} OPEN
            </Badge>
          )
        }
      />

      <AnimatePresence mode="wait">
        {positions.length > 0 ? (
          <motion.div
            key="positions"
            initial={{ opacity: 0, scale: 0.98 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.98 }}
            transition={{ type: "spring", stiffness: 300, damping: 28 }}
            className="flex flex-1 flex-col gap-4"
          >
            {positions.map((position, i) => (
              <div key={position.id}>
                {i > 0 && <div className="mb-4 border-t border-white/[0.06]" />}
                <PositionCard
                  position={position}
                  onExit={() => exit.mutate(position.id)}
                  exitPending={exit.isPending}
                />
              </div>
            ))}
          </motion.div>
        ) : (
          <motion.div
            key="flat"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="flex flex-1 flex-col items-center justify-center gap-3 text-center"
          >
            <div className="relative flex h-14 w-14 items-center justify-center">
              <span className="absolute inset-0 rounded-2xl border border-accent/20 animate-ping" />
              <motion.div
                animate={{ y: [0, -4, 0] }}
                transition={{ duration: 3, repeat: Infinity, ease: "easeInOut" }}
                className="relative flex h-14 w-14 items-center justify-center rounded-2xl border border-white/[0.07] bg-white/[0.03]"
              >
                <Crosshair size={22} className="text-slate-600" />
              </motion.div>
            </div>
            <div>
              <div className="text-sm font-medium text-slate-400">No open position</div>
              <div className="mt-1 text-xs text-slate-600">
                Waiting for a first-candle breakout setup
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </GlassCard>
  );
}

function PositionCard({
  position,
  onExit,
  exitPending,
}: {
  position: PositionView;
  onExit: () => void;
  exitPending: boolean;
}) {
  return (
    <div className="flex flex-col">
      <div className="flex items-baseline justify-between">
        <div>
          <div className="text-sm font-semibold text-white">
            NIFTY {position.strike.toFixed(0)} {position.optionType}
            <Badge
              variant={position.optionType === "CE" ? "success" : "danger"}
              className="ml-2 align-middle"
            >
              {position.optionType === "CE" ? "LONG CALL" : "LONG PUT"}
            </Badge>
            <span className="ml-1.5 font-normal text-slate-500">
              · {fmtDate(position.expiry)} exp
            </span>
          </div>
          <div className="mt-1 font-mono text-[11px] text-slate-600">
            {position.tradingsymbol}
          </div>
          <div className="mt-0.5 text-xs text-slate-500">
            {position.quantity} qty · opened {fmtTime(position.openedAt)}
          </div>
        </div>
        <div className="text-right">
          <AnimatedNumber
            value={position.unrealizedPnl}
            formatter={signedInr}
            className={cn("block text-2xl font-extrabold", pnlColor(position.unrealizedPnl))}
          />
          <div className="text-[11px] text-slate-500">unrealized</div>
        </div>
      </div>

      {/* price ladder */}
      <div className="mt-4 grid grid-cols-4 gap-2">
        <Stat label="Entry" value={position.entryPrice} />
        <Stat label="LTP" value={position.lastPrice} highlight />
        <Stat label="Stop" value={position.stopLoss} tone="loss" />
        <Stat label="T2" value={position.target2} tone="profit" />
      </div>

      {/* progress entry → T2 */}
      <ProgressRail
        entry={position.entryPrice}
        ltp={position.lastPrice}
        stop={position.stopLoss}
        target={position.target2}
      />

      {/* risk-stage ladder INITIAL → BREAKEVEN → LOCKED → TRAILING */}
      <RiskStageLadder stage={position.riskStage} />

      <div className="mt-4 flex items-center justify-end">
        <Button variant="danger" size="sm" disabled={exitPending} onClick={onExit}>
          <LogOut size={14} /> Exit now
        </Button>
      </div>
    </div>
  );
}

function Stat({
  label,
  value,
  tone,
  highlight,
}: {
  label: string;
  value: number;
  tone?: "profit" | "loss";
  highlight?: boolean;
}) {
  return (
    <motion.div
      whileHover={{ y: -2 }}
      transition={{ type: "spring", stiffness: 400, damping: 22 }}
      className={cn(
        "relative rounded-xl border border-white/[0.06] bg-white/[0.03] px-3 py-2",
        highlight && "border-accent/30 bg-accent/[0.08]"
      )}
    >
      {highlight && (
        <span className="absolute -right-1 -top-1 flex h-2 w-2">
          <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-accent-glow opacity-75" />
          <span className="relative inline-flex h-2 w-2 rounded-full bg-accent-glow" />
        </span>
      )}
      <div className="stat-label">{label}</div>
      <AnimatedNumber
        value={value}
        formatter={fmtNum}
        className={cn(
          "mt-0.5 block text-sm font-bold text-slate-100",
          tone === "profit" && "text-profit",
          tone === "loss" && "text-loss"
        )}
      />
    </motion.div>
  );
}

function ProgressRail({
  entry,
  ltp,
  stop,
  target,
}: {
  entry: number;
  ltp: number;
  stop: number;
  target: number;
}) {
  const span = target - stop || 1;
  const pct = Math.min(100, Math.max(0, ((ltp - stop) / span) * 100));
  const entryPct = Math.min(100, Math.max(0, ((entry - stop) / span) * 100));
  const climbing = ltp >= entry;

  return (
    <div className="mt-4">
      <div className="relative h-2 overflow-hidden rounded-full bg-white/[0.06]">
        <motion.div
          className={cn(
            "absolute inset-y-0 left-0 rounded-full",
            climbing ? "bg-gradient-to-r from-profit/60 to-profit" : "bg-gradient-to-r from-loss/60 to-loss"
          )}
          animate={{ width: `${pct}%` }}
          transition={{ type: "spring", stiffness: 120, damping: 20 }}
        />
        <motion.div
          className={cn(
            "absolute top-1/2 h-2.5 w-2.5 -translate-y-1/2 rounded-full shadow-[0_0_10px_2px] ",
            climbing ? "bg-profit shadow-profit/60" : "bg-loss shadow-loss/60"
          )}
          animate={{ left: `calc(${pct}% - 5px)` }}
          transition={{ type: "spring", stiffness: 120, damping: 20 }}
        />
        <div
          className="absolute inset-y-0 w-px bg-white/60"
          style={{ left: `${entryPct}%` }}
          title="Entry"
        />
      </div>
      <div className="mt-1 flex justify-between text-[10px] text-slate-600">
        <span>SL {fmtNum(stop)}</span>
        <span>T2 {fmtNum(target)}</span>
      </div>
    </div>
  );
}

function RiskStageLadder({ stage }: { stage: PositionView["riskStage"] }) {
  const activeIdx = STAGE_STEPS.findIndex((s) => s.key === stage);
  const fillPct = STAGE_STEPS.length > 1 ? (activeIdx / (STAGE_STEPS.length - 1)) * 100 : 0;

  return (
    <div className="mt-4">
      <div className="stat-label mb-2.5">Risk stage</div>
      <div className="relative flex items-start justify-between">
        {/* connecting rail */}
        <div className="absolute left-[7px] right-[7px] top-[7px] h-0.5 bg-white/[0.08]" />
        <motion.div
          className="absolute left-[7px] top-[7px] h-0.5 bg-profit"
          initial={false}
          animate={{ width: `calc(${fillPct}% - 7px)` }}
          transition={{ type: "spring", stiffness: 120, damping: 20 }}
        />
        {STAGE_STEPS.map((step, i) => {
          const done = i < activeIdx;
          const active = i === activeIdx;
          return (
            <div key={step.key} className="relative z-10 flex flex-col items-center gap-1.5">
              {active ? (
                <motion.span
                  initial={{ scale: 0.5 }}
                  animate={{ scale: 1 }}
                  transition={{ type: "spring", stiffness: 400, damping: 16 }}
                  className="h-3.5 w-3.5 rounded-full bg-accent shadow-[0_0_0_4px_rgba(99,102,241,0.25)]"
                />
              ) : done ? (
                <span className="flex h-3.5 w-3.5 items-center justify-center rounded-full bg-profit">
                  <Check size={9} className="text-base-950" strokeWidth={3} />
                </span>
              ) : (
                <span className="h-3.5 w-3.5 rounded-full border border-white/15 bg-white/[0.05]" />
              )}
              <span
                className={cn(
                  "text-[10px] leading-none transition-colors",
                  active ? "font-semibold text-accent-glow" : done ? "text-profit/80" : "text-slate-600"
                )}
              >
                {step.label}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
