import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type {
  AnalyticsSummary,
  AuditEntry,
  BrokerStatus,
  Candle,
  EquityPoint,
  OrderView,
  Page,
  PositionView,
  ScanResponse,
  SessionStatus,
  StrategyPerformanceView,
  SupertrendConfig,
  TradeRecord,
} from "@/types";

export const useBrokerStatus = () =>
  useQuery({
    queryKey: ["broker-status"],
    queryFn: () => api.get<BrokerStatus>("/broker/status"),
    refetchInterval: 15_000,
  });

export const useSessionStatus = () =>
  useQuery({
    queryKey: ["session-status"],
    queryFn: () => api.get<SessionStatus>("/session/status"),
    refetchInterval: 30_000,
  });

export const useCandles = (limit = 100) =>
  useQuery({
    queryKey: ["candles", limit],
    queryFn: () => api.get<Candle[]>(`/market/candles?limit=${limit}`),
    refetchInterval: 60_000,
  });

export const useActivePositions = () =>
  useQuery({
    queryKey: ["positions-active"],
    queryFn: () => api.get<PositionView[]>("/positions/active"),
    refetchInterval: 10_000,
  });

export const useTodayOrders = () =>
  useQuery({
    queryKey: ["orders-today"],
    queryFn: () => api.get<OrderView[]>("/orders"),
    refetchInterval: 20_000,
  });

export const useAnalyticsSummary = (period: "daily" | "weekly" | "monthly") =>
  useQuery({
    queryKey: ["analytics-summary", period],
    queryFn: () => api.get<AnalyticsSummary>(`/analytics/summary?period=${period}`),
    refetchInterval: 60_000,
  });

export const useEquityCurve = (days = 30) =>
  useQuery({
    queryKey: ["equity-curve", days],
    queryFn: () => api.get<EquityPoint[]>(`/analytics/equity-curve?days=${days}`),
  });

export const useTradeHistory = (page = 0, size = 20) =>
  useQuery({
    queryKey: ["trades", page, size],
    queryFn: () => api.get<Page<TradeRecord>>(`/history/trades?page=${page}&size=${size}`),
  });

export const useAuditLog = (page = 0) =>
  useQuery({
    queryKey: ["audit", page],
    queryFn: () => api.get<Page<AuditEntry>>(`/history/audit?page=${page}&size=50`),
  });

export const useSettings = () =>
  useQuery({
    queryKey: ["settings"],
    queryFn: () => api.get<Record<string, any>>("/settings"),
  });

export const useSessionControls = () => {
  const queryClient = useQueryClient();
  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: ["session-status"] });

  const start = useMutation({
    mutationFn: () => api.post<SessionStatus>("/session/start"),
    onSuccess: invalidate,
  });
  const stop = useMutation({
    mutationFn: () => api.post<SessionStatus>("/session/stop"),
    onSuccess: invalidate,
  });
  return { start, stop };
};

export const useExitPosition = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (positionId: number) =>
      api.post<PositionView>(`/positions/${positionId}/exit`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["positions-active"] });
      queryClient.invalidateQueries({ queryKey: ["orders-today"] });
    },
  });
};

export const useKiteLoginUrl = () =>
  useMutation({
    mutationFn: () => api.get<{ url: string }>("/broker/login-url"),
    onSuccess: (data) => {
      window.location.href = data.url;
    },
  });

export const useStrategyPerformance = () =>
  useQuery({
    queryKey: ["strategy-performance"],
    queryFn: () => api.get<StrategyPerformanceView[]>("/strategy/performance"),
    refetchInterval: 5_000,
  });

export const useSupertrendConfig = () =>
  useQuery({
    queryKey: ["supertrend-config"],
    queryFn: () => api.get<SupertrendConfig>("/strategy/supertrend-config"),
  });

export const useUpdateSupertrendConfig = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (config: SupertrendConfig) =>
      api.post<SupertrendConfig>("/strategy/supertrend-config", config),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["supertrend-config"] });
      queryClient.invalidateQueries({ queryKey: ["settings"] });
    },
  });
};

export const useGapDownScanner = () => {
  const { data } = useQuery({
    queryKey: ["gap-down-scanner"],
    queryFn: () => api.get<ScanResponse>("/stocks/gap-down-scanner"),
    refetchInterval: (query) =>
      query.state.data?.scanning ? 3_000 : 60_000,
  });
  return data ?? { stocks: [], scanTime: null, scanned: 0, scanning: false, error: null };
};

export const useRefreshGapDownScan = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api.post<ScanResponse>("/stocks/gap-down-scanner/refresh"),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["gap-down-scanner"] }),
  });
};
