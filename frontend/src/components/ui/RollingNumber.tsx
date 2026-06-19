import { cn } from "@/lib/utils";

interface RollingNumberProps {
  value: number;
  /** Formats the value to its display string (e.g. "+₹4,848.75"). */
  formatter: (value: number) => string;
  className?: string;
}

const DIGITS = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9];

/**
 * Odometer-style number: each digit lives in a 1em window over a stacked 0–9
 * column that slides on value change. Non-digit characters (₹ , . + −) render
 * static. The roll transition is defined on `.roll-col` in index.css, which is
 * disabled under Calm mode and prefers-reduced-motion.
 *
 * Accessible: the full formatted string is exposed via aria-label; the rolling
 * cells are aria-hidden so screen readers read the number once, cleanly.
 */
export function RollingNumber({ value, formatter, className }: RollingNumberProps) {
  const text = formatter(value);

  return (
    <span className={cn("num inline-flex items-baseline", className)} aria-label={text}>
      {text.split("").map((ch, i) => {
        if (ch >= "0" && ch <= "9") {
          const d = Number(ch);
          return (
            <span
              key={i}
              aria-hidden="true"
              style={{ display: "inline-block", height: "1em", overflow: "hidden", verticalAlign: "bottom" }}
            >
              <span
                className="roll-col"
                style={{ display: "flex", flexDirection: "column", transform: `translateY(-${d}em)` }}
              >
                {DIGITS.map((n) => (
                  <span key={n} style={{ height: "1em", lineHeight: "1em" }}>
                    {n}
                  </span>
                ))}
              </span>
            </span>
          );
        }
        return (
          <span key={i} aria-hidden="true">
            {ch}
          </span>
        );
      })}
    </span>
  );
}
