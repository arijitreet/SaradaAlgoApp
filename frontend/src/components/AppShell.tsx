import { useState, useCallback, useEffect, type ReactNode } from "react";
import { NavLink, useNavigate, useLocation } from "react-router-dom";
import { AnimatePresence, motion } from "framer-motion";
import {
  BarChart3,
  History,
  LayoutDashboard,
  LineChart,
  LogOut,
  Menu,
  Settings,
  Sparkles,
  Moon,
  TrendingUp,
  TrendingDown,
  Wifi,
  WifiOff,
  X,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { fmtNum, fmtSigned } from "@/lib/format";
import { useLiveStore } from "@/stores/liveStore";
import { useUiStore } from "@/stores/uiStore";
import { useAuthStore } from "@/stores/authStore";
import { AmbientBackground } from "@/components/AmbientBackground";
import { useSessionControls, useSessionStatus } from "@/hooks/queries";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { AnimatedNumber } from "@/components/ui/animated-number";

const NAV = [
  { to: "/", label: "Dashboard", icon: LayoutDashboard },
  { to: "/stocks", label: "Stocks", icon: LineChart },
  { to: "/history", label: "History", icon: History },
  { to: "/analytics", label: "Analytics", icon: BarChart3 },
  { to: "/settings", label: "Settings", icon: Settings },
];

const navContainer = {
  hidden: {},
  show: { transition: { staggerChildren: 0.06, delayChildren: 0.15 } },
};
const navItem = {
  hidden: { opacity: 0, x: -14 },
  show: { opacity: 1, x: 0 },
};

export function AppShell({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const location = useLocation();
  const logout = useAuthStore((s) => s.logout);
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const closeSidebar = useCallback(() => setSidebarOpen(false), []);

  // Close sidebar on route change
  useEffect(() => {
    setSidebarOpen(false);
  }, [location.pathname]);

  return (
    <div className="flex min-h-screen">
      <AmbientBackground />

      {/* ── Fixed left strip: logo + hamburger (always visible) ── */}
      <div className="fixed inset-y-0 left-0 z-40 flex w-[60px] flex-col items-center border-r border-white/[0.06] bg-base-900/70 backdrop-blur-2xl">
        {/* Logo */}
        <div className="flex flex-col items-center pt-4 pb-2">
          <div className="relative h-9 w-9 shrink-0">
            <div className="absolute inset-0 rounded-xl bg-gradient-to-br from-accent to-profit opacity-50 blur-md animate-glow-pulse" />
            <motion.div
              whileHover={{ scale: 1.1, rotate: -6 }}
              transition={{ type: "spring", stiffness: 320, damping: 14 }}
              className="relative h-9 w-9 overflow-hidden rounded-xl shadow-glow"
            >
              <img src="/logo.png" alt="Sarada Trading" className="h-full w-full object-cover" />
            </motion.div>
          </div>
        </div>

        {/* Hamburger toggle */}
        <button
          onClick={() => setSidebarOpen((o) => !o)}
          aria-label={sidebarOpen ? "Close menu" : "Open menu"}
          className="mt-1 flex h-9 w-9 items-center justify-center rounded-xl border border-white/10 text-slate-400 transition-colors hover:border-accent/30 hover:text-white"
        >
          {sidebarOpen ? <X size={17} /> : <Menu size={17} />}
        </button>
      </div>

      {/* ── Overlay (click-outside to close) ── */}
      <AnimatePresence>
        {sidebarOpen && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.2 }}
            onClick={closeSidebar}
            className="fixed inset-0 z-30 bg-black/40 backdrop-blur-sm"
          />
        )}
      </AnimatePresence>

      {/* ── Slide-out nav panel (only tabs + sign out, no logo) ── */}
      <AnimatePresence>
        {sidebarOpen && (
          <motion.aside
            initial={{ x: -260 }}
            animate={{ x: 0 }}
            exit={{ x: -260 }}
            transition={{ type: "spring", stiffness: 400, damping: 36 }}
            className="fixed inset-y-0 left-[60px] z-40 flex w-[196px] flex-col border-r border-white/[0.06] bg-base-900/70 backdrop-blur-2xl"
          >
            {/* Title */}
            <div className="px-4 py-5">
              <div className="text-sm font-bold tracking-tight text-white">SARADA</div>
              <div className="text-[10px] font-medium uppercase tracking-[0.18em] text-slate-500">
                Algo Terminal
              </div>
            </div>

            {/* Nav items */}
            <motion.nav
              initial="hidden"
              animate="show"
              variants={navContainer}
              className="flex-1 space-y-1 px-3"
            >
              {NAV.map(({ to, label, icon: Icon }) => (
                <motion.div key={to} variants={navItem} transition={{ type: "spring", stiffness: 320, damping: 26 }}>
                  <NavLink
                    to={to}
                    end={to === "/"}
                    className={({ isActive }) =>
                      cn(
                        "group relative flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium text-slate-400 transition-colors hover:text-slate-100",
                        isActive && "text-white"
                      )
                    }
                  >
                    {({ isActive }) => (
                      <>
                        {isActive && (
                          <motion.span
                            layoutId="nav-active"
                            className="absolute inset-0 rounded-xl border border-accent/25 bg-gradient-to-r from-accent/20 via-white/[0.06] to-transparent shadow-[0_0_24px_-8px_rgba(99,102,241,0.65)]"
                            transition={{ type: "spring", stiffness: 400, damping: 32 }}
                          />
                        )}
                        <motion.span
                          className="relative z-10 flex items-center"
                          whileHover={{ scale: 1.12 }}
                          transition={{ type: "spring", stiffness: 400, damping: 18 }}
                        >
                          <Icon size={17} />
                        </motion.span>
                        <span className="relative z-10">{label}</span>
                      </>
                    )}
                  </NavLink>
                </motion.div>
              ))}
            </motion.nav>

            <div className="border-t border-white/[0.06] p-3">
              <Button
                variant="ghost"
                className="w-full justify-start text-slate-500"
                onClick={() => {
                  logout();
                  navigate("/");
                }}
              >
                <LogOut size={16} /> Sign out
              </Button>
            </div>
          </motion.aside>
        )}
      </AnimatePresence>

      {/* ── Main column ── */}
      <div className="ml-[60px] flex min-h-screen flex-1 flex-col">
        <TopBar />
        <main className="mx-auto w-full max-w-[1500px] flex-1 px-6 py-6">{children}</main>
      </div>
    </div>
  );
}

/** Toggle between the full ambient look and the flat "calm" look. */
function CalmToggle() {
  const calm = useUiStore((s) => s.calmMode);
  const toggleCalm = useUiStore((s) => s.toggleCalm);

  return (
    <motion.button
      onClick={toggleCalm}
      whileHover={{ scale: 1.05 }}
      whileTap={{ scale: 0.94 }}
      aria-pressed={calm}
      aria-label={calm ? "Switch to ambient mode" : "Switch to calm mode"}
      title={calm ? "Ambient mode" : "Calm mode"}
      className={cn(
        "flex h-8 w-8 items-center justify-center rounded-lg border transition-colors",
        calm
          ? "border-white/10 bg-white/[0.03] text-slate-500 hover:text-slate-300"
          : "border-accent/30 bg-accent/15 text-accent-glow"
      )}
    >
      {calm ? <Moon size={15} /> : <Sparkles size={15} />}
    </motion.button>
  );
}

function TopBar() {
  const market = useLiveStore((s) => s.market);
  const connection = useLiveStore((s) => s.connection);
  const socketConnected = useLiveStore((s) => s.socketConnected);
  const liveSession = useLiveStore((s) => s.session);
  const { data: polledSession } = useSessionStatus();
  const { start, stop } = useSessionControls();

  const running = liveSession?.running ?? polledSession?.running ?? false;
  const feedUp = connection?.state === "CONNECTED" || socketConnected;
  const up = (market?.dayChange ?? 0) >= 0;

  return (
    <header className="sticky top-0 z-20 border-b border-white/[0.06] bg-base-950/70 backdrop-blur-2xl">
      <div className="mx-auto flex h-16 max-w-[1500px] items-center justify-between gap-4 px-6">
        {/* live index ticker */}
        <div className="flex items-center gap-3">
          <span className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-slate-500">
            {feedUp && (
              <span className="relative flex h-1.5 w-1.5">
                <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-accent-glow opacity-75" />
                <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-accent-glow" />
              </span>
            )}
            NIFTY 50
          </span>
          {market ? (
            <motion.div
              initial={{ opacity: 0, y: -6 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ type: "spring", stiffness: 260, damping: 24 }}
              className="flex items-baseline gap-2"
            >
              <AnimatedNumber
                value={market.ltp}
                formatter={fmtNum}
                className="text-lg font-bold text-white"
              />
              <span
                className={cn(
                  "num flex items-center gap-1 text-xs font-semibold transition-colors duration-500",
                  up ? "text-profit" : "text-loss"
                )}
              >
                <AnimatePresence mode="wait" initial={false}>
                  <motion.span
                    key={up ? "up" : "down"}
                    initial={{ opacity: 0, rotate: -45, scale: 0.6 }}
                    animate={{ opacity: 1, rotate: 0, scale: 1 }}
                    exit={{ opacity: 0, rotate: 45, scale: 0.6 }}
                    transition={{ type: "spring", stiffness: 400, damping: 20 }}
                    className="flex"
                  >
                    {up ? <TrendingUp size={13} /> : <TrendingDown size={13} />}
                  </motion.span>
                </AnimatePresence>
                <AnimatedNumber value={market.dayChange} formatter={fmtSigned} />
                <span>(<AnimatedNumber value={market.dayChangePct} formatter={(v) => `${fmtNum(v)}%`} />)</span>
              </span>
            </motion.div>
          ) : (
            <span className="text-sm text-slate-600">waiting for ticks…</span>
          )}
        </div>

        <div className="flex items-center gap-3">
          <CalmToggle />

          <Badge variant={feedUp ? "success" : "danger"} pulse={feedUp}>
            {feedUp ? <Wifi size={12} /> : <WifiOff size={12} />}
            {feedUp ? "FEED LIVE" : "FEED DOWN"}
          </Badge>

          <Badge variant={running ? "success" : "neutral"} pulse={running}>
            {running ? "SESSION ACTIVE" : "SESSION STOPPED"}
          </Badge>

          <AnimatePresence mode="wait" initial={false}>
            {running ? (
              <Button
                key="stop"
                variant="danger"
                size="sm"
                onClick={() => stop.mutate()}
                disabled={stop.isPending}
                initial={{ opacity: 0, scale: 0.9, y: -4 }}
                animate={{ opacity: 1, scale: 1, y: 0 }}
                exit={{ opacity: 0, scale: 0.9, y: 4 }}
              >
                Stop session
              </Button>
            ) : (
              <Button
                key="start"
                variant="success"
                size="sm"
                onClick={() => start.mutate()}
                disabled={start.isPending}
                initial={{ opacity: 0, scale: 0.9, y: -4 }}
                animate={{ opacity: 1, scale: 1, y: 0 }}
                exit={{ opacity: 0, scale: 0.9, y: 4 }}
              >
                Start session
              </Button>
            )}
          </AnimatePresence>
        </div>
      </div>
    </header>
  );
}
