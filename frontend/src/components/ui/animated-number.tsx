import { useEffect } from "react";
import { motion, useMotionValue, useSpring, useTransform } from "framer-motion";
import { cn } from "@/lib/utils";

interface AnimatedNumberProps {
  value: number;
  formatter: (value: number) => string;
  className?: string;
  stiffness?: number;
  damping?: number;
}

/** Smoothly tweens between numeric values instead of snapping — used for live P&L/price figures. */
export function AnimatedNumber({
  value,
  formatter,
  className,
  stiffness = 110,
  damping = 20,
}: AnimatedNumberProps) {
  const motionValue = useMotionValue(value);
  const spring = useSpring(motionValue, { stiffness, damping, mass: 0.6 });
  const display = useTransform(spring, (latest) => formatter(latest));

  useEffect(() => {
    motionValue.set(value);
  }, [value, motionValue]);

  return <motion.span className={cn("num", className)}>{display}</motion.span>;
}
