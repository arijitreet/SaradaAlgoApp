import { forwardRef, type ReactNode } from "react";
import { motion, type HTMLMotionProps } from "framer-motion";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const buttonVariants = cva(
  "group relative inline-flex items-center justify-center gap-2 overflow-hidden rounded-xl text-sm font-semibold transition-[color,background-color,border-color,box-shadow] duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/60 disabled:pointer-events-none disabled:opacity-40",
  {
    variants: {
      variant: {
        default:
          "bg-accent text-white shadow-glow hover:bg-accent-glow hover:shadow-glow-lg",
        success:
          "bg-profit/15 text-profit border border-profit/30 hover:bg-profit/25 hover:shadow-glow-profit",
        danger:
          "bg-loss/15 text-loss border border-loss/30 hover:bg-loss/25 hover:shadow-glow-loss",
        ghost:
          "text-slate-300 hover:bg-white/[0.06] hover:text-white",
        outline:
          "border border-white/10 bg-white/[0.03] text-slate-200 hover:border-white/25 hover:bg-white/[0.06]",
      },
      size: {
        default: "h-10 px-5",
        sm: "h-8 px-3 text-xs rounded-lg",
        lg: "h-12 px-7 text-base",
        icon: "h-9 w-9",
      },
    },
    defaultVariants: { variant: "default", size: "default" },
  }
);

export interface ButtonProps
  extends Omit<HTMLMotionProps<"button">, "children">,
    VariantProps<typeof buttonVariants> {
  children?: ReactNode;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, children, ...props }, ref) => (
    <motion.button
      ref={ref}
      whileHover={{ scale: 1.025, y: -1 }}
      whileTap={{ scale: 0.96, y: 0 }}
      transition={{ type: "spring", stiffness: 500, damping: 28 }}
      className={cn(buttonVariants({ variant, size }), className)}
      {...props}
    >
      <span className="relative z-10 inline-flex items-center justify-center gap-2">
        {children}
      </span>
      <span className="shine-sweep" />
    </motion.button>
  )
);
Button.displayName = "Button";
