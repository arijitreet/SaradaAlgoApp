import type { Config } from "tailwindcss";

export default {
  darkMode: "class",
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        base: {
          950: "#07090f",
          900: "#0b0e17",
          850: "#10141f",
          800: "#161b29",
        },
        accent: {
          DEFAULT: "#6366f1",
          glow: "#818cf8",
        },
        profit: "#10b981",
        loss: "#f43f5e",
      },
      fontFamily: {
        sans: ["Inter", "system-ui", "sans-serif"],
        mono: ["JetBrains Mono", "ui-monospace", "monospace"],
      },
      boxShadow: {
        glow: "0 0 40px -12px rgba(99,102,241,0.45)",
        "glow-profit": "0 0 48px -12px rgba(16,185,129,0.4)",
        "glow-loss": "0 0 48px -12px rgba(244,63,94,0.4)",
        glass: "inset 0 1px 0 0 rgba(255,255,255,0.06)",
        "glow-lg": "0 0 60px -10px rgba(99,102,241,0.55)",
        "glow-profit-lg": "0 0 70px -10px rgba(16,185,129,0.5)",
        "glow-loss-lg": "0 0 70px -10px rgba(244,63,94,0.5)",
      },
      backgroundSize: {
        "size-200": "200% 200%",
      },
      animation: {
        "pulse-slow": "pulse 3s cubic-bezier(0.4,0,0.6,1) infinite",
        shimmer: "shimmer 2.5s linear infinite",
        "gradient-x": "gradient-x 8s ease infinite",
        float: "float 6s ease-in-out infinite",
        "float-slow": "float 9s ease-in-out infinite",
        "glow-pulse": "glow-pulse 3.5s ease-in-out infinite",
        "fade-in-up": "fade-in-up 0.6s cubic-bezier(0.16,1,0.3,1) both",
        "scale-in": "scale-in 0.4s cubic-bezier(0.16,1,0.3,1) both",
        blob: "blob 14s ease-in-out infinite",
        shine: "shine 1.1s ease-in-out",
        "spin-slow": "spin 6s linear infinite",
      },
      keyframes: {
        shimmer: {
          "0%": { backgroundPosition: "-700px 0" },
          "100%": { backgroundPosition: "700px 0" },
        },
        "gradient-x": {
          "0%, 100%": { backgroundPosition: "0% 50%" },
          "50%": { backgroundPosition: "100% 50%" },
        },
        float: {
          "0%, 100%": { transform: "translateY(0px) translateX(0px)" },
          "50%": { transform: "translateY(-14px) translateX(6px)" },
        },
        "glow-pulse": {
          "0%, 100%": { opacity: "0.55", transform: "scale(1)" },
          "50%": { opacity: "1", transform: "scale(1.08)" },
        },
        "fade-in-up": {
          "0%": { opacity: "0", transform: "translateY(16px)" },
          "100%": { opacity: "1", transform: "translateY(0)" },
        },
        "scale-in": {
          "0%": { opacity: "0", transform: "scale(0.94)" },
          "100%": { opacity: "1", transform: "scale(1)" },
        },
        blob: {
          "0%, 100%": { transform: "translate(0px, 0px) scale(1)" },
          "33%": { transform: "translate(40px, -50px) scale(1.15)" },
          "66%": { transform: "translate(-30px, 30px) scale(0.9)" },
        },
        shine: {
          "0%": { transform: "translateX(-150%) skewX(-20deg)" },
          "100%": { transform: "translateX(150%) skewX(-20deg)" },
        },
      },
    },
  },
  plugins: [],
} satisfies Config;
