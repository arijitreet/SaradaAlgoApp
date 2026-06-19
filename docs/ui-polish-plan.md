# Sarada UI — "inhuman polish" plan

Goal: take the dashboard from "nice dark glass app" to "how is this real" — the
Linear/Stripe/Cred tier of smoothness — **without** hurting readability or latency
of a live, real-money trading screen. Polish is layered; we add layers bottom-up.

## Reference set (steal these, specifically)

| Site | What to steal |
|---|---|
| **linear.app** | Dark craft, instant feel, hairline gradient borders, command-menu, page transitions that never jank. The benchmark for "fast + premium." |
| **stripe.com** | Gradient mastery (animated mesh hero), scroll-linked motion, restraint. |
| **cred.club** | Premium Indian fintech feel — weighty motion, number reveals, haptic-like press. Closest in spirit to ours. |
| **vercel.com** | Dark + film grain + geometric grid backgrounds; tasteful glow. |
| **arc.net** | Joyful, springy, "alive" micro-motion. |
| **rauno.me** | Micro-interaction craft — the invisible details (focus, hover, drag) that read as "inhuman." |
| **ui.aceternity.com** | Copy-paste React/Tailwind/Framer components that match this exact look: Aurora Background, Spotlight, Background Beams, Meteors, Card Spotlight, Moving Border, Glowing Stars. **Highest-leverage for us.** |
| **magicui.design** | Animated counters, shimmer, marquee, particle/grid backgrounds. |
| **lusion.co / igloo.inc / activetheory** | Aspirational WebGL tier (3D, shaders) — for the "background that breathes." |
| **TradingView lightweight-charts / Hyperliquid** | Buttery real-time chart streaming + dark data-dense density. |

> We already installed the 21st.dev **Magic MCP** + `ui-ux-pro-max` skill — use Magic
> to generate the Aceternity-style components (aurora, spotlight cards) and adapt them
> to our tokens.

## The five polish layers

We build on what's already there: `index.css` radial gradients, `framer-motion`
springs, `GlassCard`, `AnimatedNumber`, `shadow-glow*`.

### 1. Ambient background (the "breathing" canvas)
- Replace the static radial gradients with a **slow-drifting aurora mesh** (CSS conic/
  radial keyframes, or a WebGL shader via `@react-three/fiber` + a fragment shader / `ogl`).
- **Film grain** overlay (one tiling PNG/SVG at ~3% opacity) — the Linear/Vercel matte look.
- **Cursor spotlight**: a radial highlight that follows the mouse and lifts whichever
  `GlassCard` it's over (Aceternity "Spotlight"/"Card Spotlight").
- **On-theme signature move**: tint the ambient hue subtly toward `profit`/`loss` as the
  day's P&L crosses zero — the room literally feels green/red. Distinctive, data-reactive.

### 2. Motion system (the smoothness)
- Standardize **motion tokens** (one `transition.ts`): spring presets (snappy / soft /
  bouncy), durations, stagger steps. Everything pulls from these so the whole app shares
  one rhythm — the single biggest "expensive feel" multiplier.
- **Shared-element route transitions** with Framer Motion `layoutId` — cards morph between
  Dashboard/Analytics instead of fade-swapping.
- **Staggered entrances** refined (we do some) + **exit faster than enter** (~65%).
- **Odometer number rolls** for P&L/LTP (extend `AnimatedNumber` or adopt `number-flow`).
- Strict rules: transform/opacity only, `will-change`, honor `prefers-reduced-motion`,
  target 120fps. No animating width/height/top/left.

### 3. Micro-interactions (the craft)
- **Cursor-tracking gradient borders** on cards (Aceternity "Moving Border").
- **Magnetic / tilt** on primary cards (3D `perspective` + rotateX/Y toward cursor).
- **Live-tick ripples**: a pulse ring on price update (we already ping the accent dot).
- **Skeleton shimmer** for every loading state instead of spinners.
- **Sign-flip choreography**: P&L color morphs + a 1-frame scale pop on big moves.
- Polished focus rings, press scale (0.97), optimistic toggles.

### 4. Data-viz (it's a trading app — this is the hero)
- Swap/augment Recharts with **lightweight-charts** (TradingView) or `visx` for smooth
  streaming candles + the Supertrend line overlaid live.
- **Draw-on** line charts with gradient area fills and a glow stroke.
- A breathing **P&L hero sparkline** in `PnlHeroCard`.

### 5. Theme & detail
- Refined glass: layered blur + inner highlight + 1px gradient border + depth shadow tokens.
- Consider **OKLCH** colors for smoother gradients; a distinctive display face for hero figures.
- Consistent radius/elevation scale; tabular nums everywhere (have it).

## Phased rollout (so we ship value early, save WebGL for last)

- **Phase 1 — Foundations & quick wins (highest ROI, ~low risk)**
  motion-token file · film grain · cursor spotlight on cards · shimmer skeletons ·
  odometer numbers · refined glass borders. *Pure CSS/Framer, no new heavy deps.*
- **Phase 2 — Motion system**
  shared-element route transitions · standardized stagger · sign-flip choreography ·
  magnetic/tilt cards · moving borders.
- **Phase 3 — Data-viz hero**
  lightweight-charts streaming chart + live Supertrend overlay · P&L sparkline.
- **Phase 4 — Ambient WebGL (aspirational)**
  shader aurora background (react-three-fiber) · P&L-reactive hue · optional particles.

## Guardrails (non-negotiable for a trading screen)
- **Clarity > flash.** Critical numbers (LTP, P&L, stop, position) must never be obscured,
  delayed, or made harder to read by an effect.
- **Performance budget**: 60fps floor, 120 target; no main-thread jank on tick updates;
  WebGL must pause when tab hidden / on low-power.
- **`prefers-reduced-motion`** fully respected — degrade to instant.
- **No latency cost** on the live data path; effects are presentational only.
- Kill-switch: a "calm mode" toggle that strips ambient motion for focus.

## Tooling / libraries to evaluate
`framer-motion` (have it: layout, useScroll, useSpring) · `@react-three/fiber`+`drei` or
`ogl` (shader bg) · `number-flow` (odometers) · `lenis` (smooth scroll if pages grow) ·
`lightweight-charts` / `visx` (charts) · Aceternity UI + Magic MCP (component source).
