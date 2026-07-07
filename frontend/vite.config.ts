import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";

export default defineConfig(({ mode }) => ({
  plugins: [react()],
  // react-draggable (used by react-grid-layout) reads process.env.NODE_ENV at
  // runtime; the browser has no `process`, so drags crash without this shim.
  define: {
    "process.env.NODE_ENV": JSON.stringify(mode),
  },
  resolve: {
    alias: { "@": path.resolve(__dirname, "./src") },
  },
  server: {
    port: 5173,
    proxy: {
      "/api": { target: "http://localhost:8080", changeOrigin: true },
      "/ws": { target: "http://localhost:8080", ws: true },
    },
  },
  build: {
    rollupOptions: {
      output: {
        // Peel only the heavy, leaf-like libraries into their own cacheable
        // chunks (recharts/d3 is by far the biggest and is only needed on
        // chart pages). React and everything that depends on it stay together
        // in `vendor` — splitting React out creates a vendor⇄react import cycle.
        manualChunks(id) {
          if (!id.includes("node_modules")) return;
          if (id.includes("recharts") || id.includes("d3-") || id.includes("victory-vendor"))
            return "charts";
          if (id.includes("framer-motion")) return "motion";
          if (id.includes("@stomp") || id.includes("stompjs")) return "ws";
          return "vendor";
        },
      },
    },
  },
}));
