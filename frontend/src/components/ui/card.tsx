import { type ReactNode, type MouseEvent, forwardRef } from "react";
import { motion, type HTMLMotionProps } from "framer-motion";
import { cn } from "@/lib/utils";

/**
 * Cursor-tracking spotlight: writes the pointer's position (relative to the
 * card) into --mx/--my, which the `.glass::before` radial reads to light up the
 * nearest border edge. Set imperatively so it never triggers a React re-render;
 * React leaves these custom props untouched on reconcile. Hidden in Calm mode
 * (the whole ::before is display:none there).
 */
export function trackSpotlight(e: MouseEvent<HTMLElement>) {
  const el = e.currentTarget;
  const r = el.getBoundingClientRect();
  el.style.setProperty("--mx", `${e.clientX - r.left}px`);
  el.style.setProperty("--my", `${e.clientY - r.top}px`);
}

export interface GlassCardProps extends Omit<HTMLMotionProps<"div">, "children"> {
  children?: ReactNode;
}

export const GlassCard = forwardRef<HTMLDivElement, GlassCardProps>(
  ({ className, children, ...props }, ref) => (
    <motion.div
      ref={ref}
      initial={{ opacity: 0, y: 18 }}
      animate={{ opacity: 1, y: 0 }}
      whileHover={{ y: -4 }}
      transition={{ type: "spring", stiffness: 300, damping: 28 }}
      className={cn("glass glass-hover p-5", className)}
      {...props}
      onMouseMove={trackSpotlight}
    >
      {children}
    </motion.div>
  )
);
GlassCard.displayName = "GlassCard";

export function CardHeader({
  title,
  subtitle,
  right,
}: {
  title: string;
  subtitle?: string;
  right?: React.ReactNode;
}) {
  return (
    <div className="mb-4 flex items-start justify-between gap-3">
      <div>
        <h3 className="text-sm font-semibold text-slate-100">{title}</h3>
        {subtitle && <p className="mt-0.5 text-xs text-slate-500">{subtitle}</p>}
      </div>
      {right}
    </div>
  );
}
