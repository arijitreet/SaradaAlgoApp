# REST API Contracts

Base URL: `/api` · Auth: `Authorization: Bearer <jwt>` on every endpoint except login
and the Kite OAuth callback. Errors return `{ "message", "status", "timestamp" }`.

## Auth

| Method | Path | Body | Response |
|--------|------|------|----------|
| POST | `/auth/login` | `{ "username", "password" }` | `{ "token", "username", "role" }` |

## Broker (Zerodha)

| Method | Path | Notes |
|--------|------|-------|
| GET | `/broker/login-url` | `{ "url" }` — redirect the browser here to start OAuth |
| GET | `/broker/callback?request_token=…` | Kite redirect target; 302 → frontend `/settings?broker=connected` |
| GET | `/broker/status` | `{ authenticated, kiteUserId, expiresAt, feedState, mode }` |

## Session

| Method | Path | Response |
|--------|------|----------|
| POST | `/session/start` | `{ running, reason, at }` — 422 if after 15:05 IST |
| POST | `/session/stop` | `{ running, reason, at }` |
| GET | `/session/status` | `{ running, reason, at }` |

## Market data

| Method | Path | Response |
|--------|------|----------|
| GET | `/market/candles?limit=100` | `Candle[]` oldest→newest (chart seed) |
| GET | `/market/snapshot` | `{ underlyingToken, ltp }` |

## Strategy

| Method | Path | Response |
|--------|------|----------|
| GET | `/strategy` | `[{ id, underlying, indicators, health }]` |
| GET | `/strategy/signals` | today's signals incl. accepted/rejectReason |

## Positions

| Method | Path | Response |
|--------|------|----------|
| GET | `/positions/active` | `PositionView[]` (live LTP + unrealized P&L) |
| GET | `/positions/today` | all of today's positions |
| POST | `/positions/{id}/exit` | manual exit → closed `PositionView`; 409 if already closed |

`PositionView`: `{ id, tradingsymbol, optionType, strike, expiry, quantity, entryPrice,
lastPrice, stopLoss, target1, target2, riskStage, unrealizedPnl, status, openedAt }`

## Orders

| Method | Path | Response |
|--------|------|----------|
| GET | `/orders?day=YYYY-MM-DD` | day's orders (defaults to today) |

## Analytics

| Method | Path | Response |
|--------|------|----------|
| GET | `/analytics/summary?period=daily\|weekly\|monthly` | `{ trades, wins, losses, winRatePct, grossPnl, bestTrade, worstTrade, avgPnlPerTrade, … }` |
| GET | `/analytics/equity-curve?days=30` | `[{ day, dayPnl, cumulativePnl }]` |
| GET | `/analytics/daily?days=30` | per-day stats rows |

## History & audit

| Method | Path | Response |
|--------|------|----------|
| GET | `/history/trades?page=0&size=20` | Spring `Page` of closed positions |
| GET | `/history/audit?page=0&size=50&category=ORDER` | Spring `Page` of audit entries |

## Settings

| Method | Path | Response |
|--------|------|----------|
| GET | `/settings` | effective `{ brokerMode, trading, risk, strategy }` (read-only) |
