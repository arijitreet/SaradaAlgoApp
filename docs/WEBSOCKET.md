# WebSocket (STOMP) Contracts

- Endpoint: `ws(s)://<host>/ws` (native WebSocket, STOMP 1.2)
- Authentication: STOMP CONNECT frame must carry `Authorization: Bearer <jwt>`
- All topics are broadcast (`/topic/…`); the client only subscribes, never sends.
- Frontend reference implementation: `frontend/src/lib/ws.ts` (auto-reconnect 3s,
  heartbeats 10s, subscriptions replayed on reconnect).

| Topic | Cadence | Payload |
|-------|---------|---------|
| `/topic/market` | ≤2/s | `{ token, symbol, ltp, dayChange, dayChangePct, at }` |
| `/topic/indicators` | per closed candle | `{ strategyId, indicators: { emaFast, emaSlow, vwap, atr, support, resistance, firstCandleHigh, firstCandleLow }, health: { firstCandleCaptured, candlesProcessed, indicatorsReady, emaBullish, emaBearish, aboveVwap, belowVwap, atrPass, lastEvaluation }, at }` |
| `/topic/signals` | on signal | `{ id, type: "BUY_CE"\|"BUY_PE", triggerPrice, reason, at }` |
| `/topic/orders` | on order event | full `OrderEntity` (status NEW→FILLED/REJECTED) |
| `/topic/position` | ≤2/s while open + on open/close | `PositionView` (see API.md) |
| `/topic/pnl` | with position stream + on close | `{ day, realized, unrealized, total, trades }` |
| `/topic/session` | on start/stop | `{ running, reason, at }` |
| `/topic/connection` | on feed state change | `{ state: "CONNECTED"\|"RECONNECTING"\|"DISCONNECTED"\|"AUTHENTICATED", detail, at }` |
| `/topic/feed` | on any notable event | `{ kind: "SIGNAL"\|"ORDER"\|"POSITION"\|"SESSION"\|"CONNECTION"\|"RISK", title, detail, at }` |

## Lifecycle example

```text
CONNECT  Authorization: Bearer eyJ…
SUBSCRIBE /topic/pnl
SUBSCRIBE /topic/position
← MESSAGE /topic/pnl {"day":"2026-06-10","realized":1875.00,"unrealized":-150.00,"total":1725.00,"trades":3}
← MESSAGE /topic/position {"id":42,"tradingsymbol":"NIFTY2561024750CE","riskStage":"TRAILING", …}
```
