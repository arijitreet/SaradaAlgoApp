import { useUiStore } from "@/stores/uiStore";
import { useLiveStore } from "@/stores/liveStore";

/**
 * Full-viewport ambient layer behind the entire app: three slow-drifting
 * aurora blobs (indigo / emerald / rose) plus a faint film-grain overlay.
 * Sits at -z-10 so the translucent glass surfaces blur it beautifully.
 *
 * Hue-reactive: the emerald and rose blobs brighten/fade with live day P&L,
 * tweened via the opacity transition on .aurora-blob — so the whole room leans
 * green in profit, red in loss, neutral when flat.
 *
 * Disabled entirely in Calm mode; the drift animation is also killed by the
 * prefers-reduced-motion rule in index.css.
 */
export function AmbientBackground() {
  const calm = useUiStore((s) => s.calmMode);
  const total = useLiveStore((s) => s.pnl?.total ?? 0);
  if (calm) return null;

  const emeraldOpacity = total > 0 ? 0.6 : total < 0 ? 0.16 : 0.42;
  const roseOpacity = total < 0 ? 0.45 : total > 0 ? 0.08 : 0.18;

  return (
    <div
      className="aurora-layer pointer-events-none fixed inset-0 -z-10 overflow-hidden"
      aria-hidden="true"
    >
      <div
        className="aurora-blob"
        style={{
          width: 620,
          height: 620,
          left: "-12%",
          top: "-18%",
          background: "radial-gradient(circle, rgba(99,102,241,0.55), transparent 62%)",
          animation: "aurora-1 26s ease-in-out infinite",
        }}
      />
      <div
        className="aurora-blob"
        style={{
          width: 560,
          height: 560,
          right: "-14%",
          bottom: "-20%",
          opacity: emeraldOpacity,
          background: "radial-gradient(circle, rgba(16,185,129,0.42), transparent 62%)",
          animation: "aurora-2 32s ease-in-out infinite",
        }}
      />
      <div
        className="aurora-blob"
        style={{
          width: 420,
          height: 420,
          left: "42%",
          top: "-10%",
          opacity: roseOpacity,
          background: "radial-gradient(circle, rgba(244,63,94,0.20), transparent 60%)",
          animation: "aurora-3 38s ease-in-out infinite",
        }}
      />
      <div className="grain" />
    </div>
  );
}
