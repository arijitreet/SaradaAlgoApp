import { Client, type IMessage } from "@stomp/stompjs";
import { getAuthToken } from "./api";

type Handler = (payload: unknown) => void;

/**
 * Singleton STOMP client over native WebSocket with automatic reconnect.
 * Subscriptions are registered locally and replayed on every (re)connect.
 */
class LiveSocket {
  private client: Client | null = null;
  private handlers = new Map<string, Set<Handler>>();
  private statusListeners = new Set<(connected: boolean) => void>();

  private wsUrl(): string {
    const configured = import.meta.env.VITE_WS_URL ?? "/ws";
    if (configured.startsWith("ws")) return configured;
    const proto = window.location.protocol === "https:" ? "wss" : "ws";
    return `${proto}://${window.location.host}${configured}`;
  }

  connect() {
    if (this.client?.active) return;
    this.client = new Client({
      brokerURL: this.wsUrl(),
      connectHeaders: { Authorization: `Bearer ${getAuthToken() ?? ""}` },
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this.statusListeners.forEach((l) => l(true));
        for (const topic of this.handlers.keys()) this.doSubscribe(topic);
      },
      onWebSocketClose: () => this.statusListeners.forEach((l) => l(false)),
    });
    this.client.activate();
  }

  disconnect() {
    this.client?.deactivate();
    this.client = null;
  }

  onStatus(listener: (connected: boolean) => void) {
    this.statusListeners.add(listener);
    return () => this.statusListeners.delete(listener);
  }

  subscribe(topic: string, handler: Handler): () => void {
    let set = this.handlers.get(topic);
    const isNewTopic = !set;
    if (!set) {
      set = new Set();
      this.handlers.set(topic, set);
    }
    set.add(handler);
    if (isNewTopic && this.client?.connected) this.doSubscribe(topic);
    return () => {
      set!.delete(handler);
    };
  }

  private doSubscribe(topic: string) {
    this.client?.subscribe(topic, (message: IMessage) => {
      const payload = JSON.parse(message.body);
      this.handlers.get(topic)?.forEach((handler) => handler(payload));
    });
  }
}

export const liveSocket = new LiveSocket();

export const TOPICS = {
  market: "/topic/market",
  indicators: "/topic/indicators",
  signals: "/topic/signals",
  orders: "/topic/orders",
  position: "/topic/position",
  pnl: "/topic/pnl",
  session: "/topic/session",
  connection: "/topic/connection",
  feed: "/topic/feed",
} as const;
