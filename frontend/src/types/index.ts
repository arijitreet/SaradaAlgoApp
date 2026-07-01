export interface MarketUpdate {
  token: number;
  symbol: string;
  ltp: number;
  dayChange: number;
  dayChangePct: number;
  at: string;
}

export interface Candle {
  instrumentToken: number;
  symbol: string;
  intervalMinutes: number;
  openTime: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface IndicatorSnapshot {
  emaFast: number | null;
  emaSlow: number | null;
  vwap: number | null;
  atr: number | null;
  support: number | null;
  resistance: number | null;
  firstCandleHigh: number | null;
  firstCandleLow: number | null;
  // Supertrend fields — null for non-supertrend strategies
  supertrendLine: number | null;
  supertrendBullish: boolean | null;
  // Mean Reversion / VWAP extras — null when not applicable
  bbUpper: number | null;
  bbMiddle: number | null;
  bbLower: number | null;
  rsi: number | null;
  adx: number | null;
}

export interface StrategyHealth {
  firstCandleCaptured: boolean;
  candlesProcessed: number;
  indicatorsReady: boolean;
  emaBullish: boolean;
  emaBearish: boolean;
  aboveVwap: boolean;
  belowVwap: boolean;
  atrPass: boolean;
  lastEvaluation: string;
}

export interface IndicatorsView {
  strategyId: string;
  indicators: IndicatorSnapshot;
  health: StrategyHealth;
  at: string;
}

export interface PositionView {
  id: number;
  tradingsymbol: string;
  optionType: "CE" | "PE";
  strike: number;
  expiry: string;
  quantity: number;
  entryPrice: number;
  lastPrice: number;
  stopLoss: number;
  target1: number;
  target2: number;
  riskStage: "INITIAL" | "BREAKEVEN" | "LOCKED" | "TRAILING";
  unrealizedPnl: number;
  status: "OPEN" | "CLOSED";
  openedAt: string;
}

export interface PnlSummary {
  day: string;
  realized: number;
  unrealized: number;
  total: number;
  trades: number;
}

export interface SessionStatus {
  running: boolean;
  reason: string | null;
  at: string;
}

export interface ConnectionView {
  state: "CONNECTED" | "DISCONNECTED" | "RECONNECTING" | "AUTHENTICATED";
  detail: string;
  at: string;
}

export interface FeedEntry {
  kind: string;
  title: string;
  detail: string;
  at: string;
}

export interface SignalView {
  id: number;
  type: "BUY_CE" | "BUY_PE";
  triggerPrice: number;
  reason: string;
  at: string;
}

export interface SupertrendConfig {
  atrPeriod: number;
  multiplier: number;
  strikeOffset: number;
}

export interface StrategyPerformanceView {
  strategyId: string;
  active: boolean;
  realizedPnl: number;
  unrealizedPnl: number;
  totalPnl: number;
  trades: number;
  maxTrades: number;
  openPosition: {
    tradingsymbol: string;
    optionType: "CE" | "PE";
    strike: number;
    unrealizedPnl: number;
  } | null;
  lastSignal: {
    type: string;
    triggerPrice: number;
    at: string;
  } | null;
  activeWindow: string;
  windowActive: boolean;
}

export interface OrderView {
  id: number;
  brokerOrderId: string | null;
  tradingDay: string;
  tradingsymbol: string;
  side: "BUY" | "SELL";
  quantity: number;
  avgFillPrice: number | null;
  status: string;
  statusMessage: string | null;
  mode: string;
  placedAt: string;
  filledAt: string | null;
}

export interface BrokerStatus {
  authenticated: boolean;
  kiteUserId: string | null;
  expiresAt: string | null;
  feedState: "CONNECTED" | "CONNECTING" | "DISCONNECTED";
  mode: "LIVE";
}

export interface AnalyticsSummary {
  period: string;
  from: string;
  to: string;
  trades: number;
  wins: number;
  losses: number;
  winRatePct: number;
  grossPnl: number;
  bestTrade: number;
  worstTrade: number;
  avgPnlPerTrade: number;
}

export interface EquityPoint {
  day: string;
  dayPnl: number;
  cumulativePnl: number;
}

export interface TradeRecord {
  id: number;
  tradingDay: string;
  tradingsymbol: string;
  optionType: "CE" | "PE";
  strike: number;
  expiry: string;
  quantity: number;
  entryPrice: number;
  exitPrice: number | null;
  exitReason: string | null;
  realizedPnl: number | null;
  riskStage: string;
  status: string;
  openedAt: string;
  closedAt: string | null;
}

export interface AuditEntry {
  id: number;
  category: string;
  action: string;
  detail: string | null;
  actor: string;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export interface GapDownStock {
  symbol: string;
  ltp: number;
  todayOpen: number;
  fifteenthDayClose: number;
  gapDownPercent: number;
  fifteenDayHigh: number;
  fifteenDayLow: number;
}

export interface ScanResponse {
  stocks: GapDownStock[];
  scanTime: string | null;
  scanned: number;
  scanning: boolean;
  error: string | null;
}
