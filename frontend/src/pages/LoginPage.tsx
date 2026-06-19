import { useState, type FormEvent } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { Loader2, Lock, User } from "lucide-react";
import { useAuthStore } from "@/stores/authStore";
import { Button } from "@/components/ui/button";

const formContainer = {
  hidden: {},
  show: { transition: { staggerChildren: 0.08, delayChildren: 0.25 } },
};
const formItem = {
  hidden: { opacity: 0, y: 14 },
  show: { opacity: 1, y: 0, transition: { type: "spring", stiffness: 320, damping: 26 } },
};

export default function LoginPage() {
  const { login, loggingIn, error } = useAuthStore();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    try {
      await login(username, password);
    } catch {
      /* error surfaced via store */
    }
  };

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden px-4">
      {/* ambient background orbs */}
      <div className="orb -left-32 top-1/4 h-80 w-80 bg-accent/20 animate-blob" />
      <div className="orb right-0 top-0 h-96 w-96 bg-profit/10 animate-blob animation-delay-2000" />
      <div className="orb bottom-0 left-1/3 h-72 w-72 bg-accent-glow/10 animate-blob animation-delay-4000" />

      <motion.div
        initial={{ opacity: 0, y: 24, scale: 0.97 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ type: "spring", stiffness: 240, damping: 24 }}
        className="glass relative w-full max-w-sm overflow-hidden p-8 shadow-glow-lg"
      >
        <motion.div
          initial="hidden"
          animate="show"
          variants={formContainer}
          className="mb-8 flex flex-col items-center gap-3 text-center"
        >
          <motion.div variants={formItem} className="relative h-16 w-16">
            <div className="absolute inset-0 rounded-2xl bg-gradient-to-br from-accent to-profit opacity-50 blur-xl animate-glow-pulse" />
            <motion.div
              animate={{ y: [0, -6, 0] }}
              transition={{ duration: 4, repeat: Infinity, ease: "easeInOut" }}
              className="relative h-16 w-16 overflow-hidden rounded-2xl shadow-glow"
            >
              <img src="/logo.png" alt="Sarada Trading" className="h-full w-full object-cover" />
            </motion.div>
          </motion.div>
          <motion.div variants={formItem}>
            <h1 className="text-xl font-bold tracking-tight text-white">
              <span className="text-gradient-animate bg-gradient-to-r from-accent via-accent-glow to-profit">
                SARADA
              </span>
            </h1>
            <p className="mt-1 text-xs uppercase tracking-[0.2em] text-slate-500">
              Algo Trading Terminal
            </p>
          </motion.div>
        </motion.div>

        <motion.form
          initial="hidden"
          animate="show"
          variants={formContainer}
          onSubmit={submit}
          className="space-y-4"
        >
          <motion.div variants={formItem}>
            <Field
              icon={<User size={15} />}
              type="text"
              placeholder="Username"
              value={username}
              onChange={setUsername}
            />
          </motion.div>
          <motion.div variants={formItem}>
            <Field
              icon={<Lock size={15} />}
              type="password"
              placeholder="Password"
              value={password}
              onChange={setPassword}
            />
          </motion.div>

          <AnimatePresence>
            {error && (
              <motion.p
                initial={{ opacity: 0, y: -6, height: 0 }}
                animate={{ opacity: 1, y: 0, height: "auto" }}
                exit={{ opacity: 0, height: 0 }}
                transition={{ type: "spring", stiffness: 400, damping: 28 }}
                className="overflow-hidden rounded-lg border border-loss/25 bg-loss/10 px-3 py-2 text-xs text-loss"
              >
                {error}
              </motion.p>
            )}
          </AnimatePresence>

          <motion.div variants={formItem}>
            <Button className="w-full" size="lg" disabled={loggingIn || !username || !password}>
              <AnimatePresence mode="wait" initial={false}>
                {loggingIn ? (
                  <motion.span
                    key="loading"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    className="flex items-center gap-2"
                  >
                    <Loader2 size={15} className="animate-spin" /> Signing in…
                  </motion.span>
                ) : (
                  <motion.span
                    key="idle"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                  >
                    Sign in
                  </motion.span>
                )}
              </AnimatePresence>
            </Button>
          </motion.div>
        </motion.form>
      </motion.div>
    </div>
  );
}

function Field({
  icon,
  type,
  placeholder,
  value,
  onChange,
}: {
  icon: React.ReactNode;
  type: string;
  placeholder: string;
  value: string;
  onChange: (v: string) => void;
}) {
  return (
    <div className="relative">
      <span className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-500">
        {icon}
      </span>
      <input
        type={type}
        placeholder={placeholder}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        autoComplete={type === "password" ? "current-password" : "username"}
        className="input-glow h-11 w-full rounded-xl border border-white/10 bg-white/[0.04] pl-10 pr-4 text-sm text-white placeholder:text-slate-600 outline-none transition-colors focus:border-accent/50 focus:bg-white/[0.06]"
      />
    </div>
  );
}
