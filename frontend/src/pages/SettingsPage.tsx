import { motion } from "framer-motion";
import { Link2, ShieldCheck, Cpu, SlidersHorizontal } from "lucide-react";
import { fmtTime, fmtDate } from "@/lib/format";
import { useBrokerStatus, useKiteLoginUrl, useSettings } from "@/hooks/queries";
import { GlassCard, CardHeader } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { SupertrendConfigForm } from "@/components/SupertrendConfigForm";

const rowContainer = {
  hidden: {},
  show: { transition: { staggerChildren: 0.05, delayChildren: 0.1 } },
};
const rowItem = {
  hidden: { opacity: 0, x: -10 },
  show: { opacity: 1, x: 0, transition: { type: "spring", stiffness: 320, damping: 26 } },
};

export default function SettingsPage() {
  return (
    <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
      <BrokerCard />
      <RuntimeCard />
      <RiskCard />
      <StrategyCard />
      <SupertrendCard />
    </div>
  );
}

function BrokerCard() {
  const { data: broker } = useBrokerStatus();
  const kiteLogin = useKiteLoginUrl();

  return (
    <GlassCard transition={{ type: "spring", stiffness: 280, damping: 28 }}>
      <CardHeader
        title="Broker Connection"
        subtitle="Zerodha Kite Connect"
        right={
          <Badge variant={broker?.authenticated ? "success" : "danger"} pulse={broker?.authenticated}>
            {broker?.authenticated ? "CONNECTED" : "NOT CONNECTED"}
          </Badge>
        }
      />
      <motion.div initial="hidden" animate="show" variants={rowContainer} className="space-y-3 text-sm">
        <Row label="Mode">
          <Badge variant="danger">LIVE</Badge>
        </Row>
        <Row label="Kite user">{broker?.kiteUserId ?? "—"}</Row>
        <Row label="Feed state">{broker?.feedState ?? "—"}</Row>
        <Row label="Token expires">
          {broker?.expiresAt ? `${fmtDate(broker.expiresAt)} ${fmtTime(broker.expiresAt)}` : "—"}
        </Row>
      </motion.div>
      <div className="mt-5 border-t border-white/[0.06] pt-4">
        <Button onClick={() => kiteLogin.mutate()} disabled={kiteLogin.isPending}>
          <Link2 size={15} />
          {broker?.authenticated ? "Re-authenticate with Kite" : "Connect Zerodha account"}
        </Button>
        <p className="mt-3 text-[11px] leading-relaxed text-slate-600">
          You'll be redirected to kite.trade to approve access. The daily access token is
          AES-256 encrypted at rest and never leaves the backend.
        </p>
      </div>
    </GlassCard>
  );
}

function RuntimeCard() {
  const { data: settings } = useSettings();
  const trading = settings?.trading;

  return (
    <GlassCard transition={{ delay: 0.06, type: "spring", stiffness: 280, damping: 28 }}>
      <CardHeader
        title="Trading Session"
        subtitle="Effective runtime configuration"
        right={<Cpu size={16} className="text-slate-600" />}
      />
      <motion.div initial="hidden" animate="show" variants={rowContainer} className="space-y-3 text-sm">
        <Row label="Underlying">{trading?.underlying ?? "—"}</Row>
        <Row label="Session window">
          {trading ? `${trading.sessionStart} – ${trading.sessionEnd} IST` : "—"}
        </Row>
        <Row label="Candle interval">{trading ? `${trading.candleMinutes} min` : "—"}</Row>
        <Row label="Max trades / day">{trading?.maxTradesPerDay ?? "—"}</Row>
        <Row label="Quantity">
          {trading ? `${trading.quantityLots} lot × ${trading.lotSize}` : "—"}
        </Row>
      </motion.div>
      <Note />
    </GlassCard>
  );
}

function RiskCard() {
  const { data: settings } = useSettings();
  const risk = settings?.risk;

  return (
    <GlassCard transition={{ delay: 0.12, type: "spring", stiffness: 280, damping: 28 }}>
      <CardHeader
        title="Risk Rules"
        subtitle="Stop-loss ladder (premium points)"
        right={<ShieldCheck size={16} className="text-slate-600" />}
      />
      <motion.div initial="hidden" animate="show" variants={rowContainer} className="space-y-3 text-sm">
        <Row label="Initial stop">entry − {risk?.stopLossPoints ?? "—"}</Row>
        <Row label="Target 1">+{risk?.target1Points ?? "—"} → stop to breakeven</Row>
        <Row label="Target 2">
          +{risk?.target2Points ?? "—"} → stop to entry +{risk?.target2StopOffset ?? "—"}
        </Row>
        <Row label="Trailing">
          +{risk?.trailStepPoints ?? "—"} stop per additional +{risk?.trailStepPoints ?? "—"} gain
        </Row>
        <Row label="Force exit">15:05 IST · all positions flattened</Row>
      </motion.div>
    </GlassCard>
  );
}

function StrategyCard() {
  const { data: settings } = useSettings();
  const fcb = settings?.strategy?.firstCandleBreakout;

  return (
    <GlassCard transition={{ delay: 0.18, type: "spring", stiffness: 280, damping: 28 }}>
      <CardHeader
        title="Strategy — First Candle Breakout"
        subtitle="first-candle-breakout-v1"
        right={<SlidersHorizontal size={16} className="text-slate-600" />}
      />
      <motion.div initial="hidden" animate="show" variants={rowContainer} className="space-y-3 text-sm">
        <Row label="EMA fast / slow">
          {fcb ? `${fcb.emaFast} / ${fcb.emaSlow}` : "—"}
        </Row>
        <Row label="ATR period">{fcb?.atrPeriod ?? "—"}</Row>
        <Row label="Min ATR filter">{fcb?.minAtrPoints ?? "—"} pts</Row>
        <Row label="S/R lookback">{fcb?.srLookback ?? "—"} candles</Row>
      </motion.div>
      <Note />
    </GlassCard>
  );
}

function SupertrendCard() {
  return (
    <GlassCard transition={{ delay: 0.24, type: "spring", stiffness: 280, damping: 28 }}>
      <CardHeader
        title="Strategy — Supertrend Flip"
        subtitle="supertrend-flip-v1 · editable live"
        right={<SlidersHorizontal size={16} className="text-slate-600" />}
      />
      <SupertrendConfigForm />
    </GlassCard>
  );
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <motion.div
      variants={rowItem}
      className="flex items-center justify-between gap-4 border-b border-white/[0.04] pb-2.5 last:border-0 last:pb-0"
    >
      <span className="text-slate-500">{label}</span>
      <span className="num text-right font-medium text-slate-200">{children}</span>
    </motion.div>
  );
}

function Note() {
  return (
    <motion.p
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ delay: 0.4 }}
      className="mt-4 rounded-xl border border-white/[0.05] bg-white/[0.02] px-3 py-2.5 text-[11px] leading-relaxed text-slate-600"
    >
      Parameters are managed via deployment environment variables so the UI and the engine
      can never disagree. Edit <span className="font-mono">.env</span> /{" "}
      <span className="font-mono">application.yml</span> and restart to change.
    </motion.p>
  );
}
