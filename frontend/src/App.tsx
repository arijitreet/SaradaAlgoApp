import { lazy, Suspense, useEffect, type ReactNode } from "react";
import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import { AnimatePresence, motion } from "framer-motion";
import { useAuthStore } from "@/stores/authStore";
import { useLiveStore } from "@/stores/liveStore";
import { AppShell } from "@/components/AppShell";
import LoginPage from "@/pages/LoginPage";

// Route-level code splitting: each page becomes its own chunk, so the initial
// dashboard load no longer ships History / Analytics / Settings (and the heavy
// recharts code they pull in) until the user actually navigates there.
const DashboardPage = lazy(() => import("@/pages/DashboardPage"));
const StocksPage = lazy(() => import("@/pages/StocksPage"));
const HistoryPage = lazy(() => import("@/pages/HistoryPage"));
const AnalyticsPage = lazy(() => import("@/pages/AnalyticsPage"));
const SettingsPage = lazy(() => import("@/pages/SettingsPage"));

function PageTransition({ children }: { children: ReactNode }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -12 }}
      transition={{ duration: 0.25, ease: [0.16, 1, 0.3, 1] }}
    >
      {children}
    </motion.div>
  );
}

export default function App() {
  const token = useAuthStore((s) => s.token);
  const startLive = useLiveStore((s) => s.start);
  const location = useLocation();

  useEffect(() => {
    if (token) startLive();
  }, [token, startLive]);

  if (!token) {
    return <LoginPage />;
  }

  return (
    <AppShell>
      <AnimatePresence mode="wait" initial={false}>
        <Suspense
          fallback={
            <div className="flex h-[60vh] items-center justify-center text-sm text-slate-600">
              Loading…
            </div>
          }
        >
          <Routes location={location} key={location.pathname}>
            <Route path="/" element={<PageTransition><DashboardPage /></PageTransition>} />
            <Route path="/stocks" element={<PageTransition><StocksPage /></PageTransition>} />
            <Route path="/history" element={<PageTransition><HistoryPage /></PageTransition>} />
            <Route path="/analytics" element={<PageTransition><AnalyticsPage /></PageTransition>} />
            <Route path="/settings" element={<PageTransition><SettingsPage /></PageTransition>} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </Suspense>
      </AnimatePresence>
    </AppShell>
  );
}
