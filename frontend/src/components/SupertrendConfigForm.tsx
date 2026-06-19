import { useEffect, useState } from "react";
import { motion } from "framer-motion";
import { Check, AlertTriangle } from "lucide-react";
import { cn } from "@/lib/utils";
import { useSupertrendConfig, useUpdateSupertrendConfig } from "@/hooks/queries";
import { Button } from "@/components/ui/button";

const OFFSETS = [
  { value: 0, label: "ATM" },
  { value: 1, label: "1 OTM" },
  { value: 2, label: "2 OTM" },
];

/** Editable Supertrend Flip parameters, applied live via POST /api/strategy/supertrend-config. */
export function SupertrendConfigForm() {
  const { data, isLoading } = useSupertrendConfig();
  const update = useUpdateSupertrendConfig();

  const [atrPeriod, setAtrPeriod] = useState(10);
  const [multiplier, setMultiplier] = useState(3);
  const [strikeOffset, setStrikeOffset] = useState(0);

  // Seed local state once the server config arrives (and after a successful save refetch).
  useEffect(() => {
    if (data) {
      setAtrPeriod(data.atrPeriod);
      setMultiplier(data.multiplier);
      setStrikeOffset(data.strikeOffset);
    }
  }, [data]);

  const dirty =
    !!data &&
    (data.atrPeriod !== atrPeriod ||
      data.multiplier !== multiplier ||
      data.strikeOffset !== strikeOffset);

  const indicatorWillReset =
    !!data && (data.atrPeriod !== atrPeriod || data.multiplier !== multiplier);

  const save = () => {
    const clampedAtr = Math.min(50, Math.max(2, Math.round(atrPeriod) || 10));
    update.mutate({ atrPeriod: clampedAtr, multiplier, strikeOffset });
  };

  if (isLoading) {
    return <div className="py-6 text-sm text-slate-500">Loading config…</div>;
  }

  return (
    <div className="space-y-4">
      {/* ATR period */}
      <div className="flex items-center justify-between gap-4">
        <label htmlFor="st-atr" className="text-sm text-slate-400">
          ATR period
        </label>
        <input
          id="st-atr"
          type="number"
          min={2}
          max={50}
          step={1}
          value={atrPeriod}
          onChange={(e) => setAtrPeriod(Number(e.target.value))}
          className="input-glow num w-20 rounded-lg border border-white/10 bg-white/[0.04] px-3 py-1.5 text-right text-sm text-slate-100 outline-none focus:border-accent/50"
        />
      </div>

      {/* Multiplier */}
      <div>
        <div className="mb-1.5 flex items-center justify-between">
          <label htmlFor="st-mult" className="text-sm text-slate-400">
            Multiplier
          </label>
          <span className="num text-sm font-semibold text-accent-glow">{multiplier.toFixed(1)}</span>
        </div>
        <input
          id="st-mult"
          type="range"
          min={1}
          max={6}
          step={0.5}
          value={multiplier}
          onChange={(e) => setMultiplier(Number(e.target.value))}
          className="w-full accent-accent"
        />
      </div>

      {/* Strike offset */}
      <div>
        <div className="mb-1.5 text-sm text-slate-400">Strike offset</div>
        <div className="flex gap-2">
          {OFFSETS.map((o) => (
            <button
              key={o.value}
              type="button"
              onClick={() => setStrikeOffset(o.value)}
              className={cn(
                "flex-1 rounded-lg border px-3 py-1.5 text-xs font-medium transition-colors",
                strikeOffset === o.value
                  ? "border-accent/50 bg-accent/15 text-accent-glow"
                  : "border-white/10 bg-white/[0.03] text-slate-400 hover:bg-white/[0.06]"
              )}
            >
              {o.label}
            </button>
          ))}
        </div>
      </div>

      {indicatorWillReset && (
        <motion.div
          initial={{ opacity: 0, y: 4 }}
          animate={{ opacity: 1, y: 0 }}
          className="flex items-start gap-2 rounded-lg border border-amber-400/20 bg-amber-400/[0.06] px-3 py-2 text-[11px] leading-relaxed text-amber-300/90"
        >
          <AlertTriangle size={13} className="mt-0.5 shrink-0" />
          Changing ATR period or multiplier rebuilds the indicator and restarts warm-up — the
          strategy won't fire flips until it re-seeds.
        </motion.div>
      )}

      <div className="flex items-center justify-between pt-1">
        <span className="text-[11px] text-slate-600">
          {update.isSuccess && !dirty ? "Saved · applied live" : "Applied live on save"}
        </span>
        <Button size="sm" disabled={!dirty || update.isPending} onClick={save}>
          <Check size={14} /> {update.isPending ? "Saving…" : "Save & apply"}
        </Button>
      </div>

      {update.isError && (
        <div className="text-[11px] text-loss">
          {(update.error as Error)?.message ?? "Update failed"}
        </div>
      )}
    </div>
  );
}
