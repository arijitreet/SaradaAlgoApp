import { create } from "zustand";
import type {
  ConnectionView,
  FeedEntry,
  IndicatorsView,
  MarketUpdate,
  PnlSummary,
  PositionView,
  SessionStatus,
  SignalView,
} from "@/types";
import { liveSocket, TOPICS } from "@/lib/ws";

interface LiveState {
  socketConnected: boolean;
  market: MarketUpdate | null;
  /** All strategies keyed by strategyId — updated on every /topic/indicators message. */
  indicatorsMap: Record<string, IndicatorsView>;
  /** Currently selected strategy; defaults to first strategy received via WS. */
  selectedStrategyId: string | null;
  /** Convenience: indicators for the currently selected strategy (or null if none). */
  indicators: IndicatorsView | null;
  /** Every currently OPEN position, keyed by id — up to maxConcurrentTrades entries. */
  positions: Record<number, PositionView>;
  pnl: PnlSummary | null;
  session: SessionStatus | null;
  connection: ConnectionView | null;
  feed: FeedEntry[];
  lastSignal: SignalView | null;
  setSelectedStrategy: (id: string) => void;
  start: () => void;
}

let started = false;

export const useLiveStore = create<LiveState>((set, get) => ({
  socketConnected: false,
  market: null,
  indicatorsMap: {},
  selectedStrategyId: null,
  indicators: null,
  positions: {},
  pnl: null,
  session: null,
  connection: null,
  feed: [],
  lastSignal: null,

  setSelectedStrategy: (id: string) => {
    const { indicatorsMap } = get();
    set({
      selectedStrategyId: id,
      indicators: indicatorsMap[id] ?? null,
    });
  },

  start: () => {
    if (started) return;
    started = true;

    liveSocket.onStatus((connected) => set({ socketConnected: connected }));
    liveSocket.subscribe(TOPICS.market, (p) => set({ market: p as MarketUpdate }));

    liveSocket.subscribe(TOPICS.indicators, (p) => {
      const view = p as IndicatorsView;
      set((state) => {
        const indicatorsMap = { ...state.indicatorsMap, [view.strategyId]: view };
        // Auto-select the first strategy that comes in; preserve manual selection after that.
        const selectedStrategyId = state.selectedStrategyId ?? view.strategyId;
        return {
          indicatorsMap,
          selectedStrategyId,
          indicators: indicatorsMap[selectedStrategyId] ?? view,
        };
      });
    });

    liveSocket.subscribe(TOPICS.pnl, (p) => set({ pnl: p as PnlSummary }));
    liveSocket.subscribe(TOPICS.session, (p) => set({ session: p as SessionStatus }));
    liveSocket.subscribe(TOPICS.connection, (p) => set({ connection: p as ConnectionView }));
    liveSocket.subscribe(TOPICS.signals, (p) => set({ lastSignal: p as SignalView }));
    liveSocket.subscribe(TOPICS.position, (p) => {
      const position = p as PositionView;
      set((state) => {
        const positions = { ...state.positions };
        if (position.status === "OPEN") {
          positions[position.id] = position;
        } else {
          delete positions[position.id];
        }
        return { positions };
      });
    });
    liveSocket.subscribe(TOPICS.feed, (p) => {
      const entry = p as FeedEntry;
      set({ feed: [entry, ...get().feed].slice(0, 60) });
    });
    liveSocket.connect();
  },
}));
