# Sarada — Automated Nifty Options Trading Platform

Algorithmic trading platform for Nifty 50 weekly options via Zerodha Kite Connect. Runs as a single Java process — no Docker, PostgreSQL, or Redis required.

| Layer    | Stack |
|----------|-------|
| Backend  | Java 17 · Spring Boot 3.2 · Spring Security (JWT) · Spring WebSocket (STOMP) · H2 (embedded file DB) · Flyway |
| Broker   | Zerodha Kite Connect v3.5 (REST orders + KiteTicker WebSocket) |
| Frontend | React 18 · TypeScript · Vite 5 · Tailwind CSS · Framer Motion · Recharts · Zustand · TanStack Query |

---

## Features

- **Two concurrent strategies** — First Candle Breakout (FCB) and Supertrend Flip
- **Live strategy config** — edit Supertrend ATR period, multiplier and strike offset from the dashboard without a restart
- **Paper trading mode** — simulated fills with a demo feed; no real orders, no Kite credentials needed
- **Real-time dashboard** — WebSocket-pushed P&L odometer, active position with risk-stage ladder, per-strategy comparison, indicator snapshot, activity feed
- **Automatic risk management** — stop-loss, two profit targets, trailing stop, per-day trade cap, force-exit at session end
- **Glassmorphism UI** — aurora background, cursor-tracking border spotlight, calm mode toggle

---

## Architecture

Modular monolith with Clean Architecture + DDD. Modules communicate only through `DomainEvents` and ports — never through each other's `infra` layer.

```
backend/src/main/java/com/sarada/trading/
├── common/       events, audit, security, crypto, WebSocket publisher, TradingClock
├── auth/         JWT login
├── broker/       BrokerGateway port → KiteBrokerGateway (live) + PaperBrokerGateway
├── marketdata/   KiteTicker, 5-min candle aggregation, option selector
├── strategy/     StrategyEngine, FCB + Supertrend strategies, indicators, SupertrendConfigService
├── risk/         SL / target / trailing-stop policy, session window, force-exit
├── orders/       order lifecycle + persistence
├── positions/    live P&L, position monitoring, StrategyPerformancePort impl
├── analytics/    daily aggregates, equity curve
└── history/      trade history queries
```

**Event flow**
```
KiteTicker ──TickReceived──▶ CandleAggregator ──CandleClosed──▶ StrategyEngine
StrategyEngine ──SignalGenerated──▶ RiskManager ──▶ OptionSelector ──▶ OrderService
OrderService ──OrderFilled──▶ PositionService ──▶ PositionMonitor (SL / targets / trail)
PositionMonitor ──PositionClosed──▶ TradeHistory + Analytics + WS push
```

---

## Prerequisites

- Java 17+
- Maven 3.8+
- Node.js 20+ / npm
- A Zerodha Kite Connect developer app at [developers.kite.trade](https://developers.kite.trade)
- A **static public IP** on your server (see [IP Whitelist](#ip-whitelist) below)

---

## Quick Start

### 1. Clone and configure

```bash
git clone https://github.com/arijitreet/SaradaAlgoApp.git
cd SaradaAlgoApp
cp .env.example .env        # also copy to backend/.env if running from backend/
```

Edit `.env` — all required keys are documented in `.env.example`.

### 2. Development (two terminals)

```bash
# Terminal 1 — backend
cd backend && mvn spring-boot:run

# Terminal 2 — frontend (hot reload, proxies /api and /ws to :8080)
cd frontend && npm install && npm run dev
# Dashboard at http://localhost:5173
```

### 3. Production (single port)

```bash
cd frontend && npm install && npm run build
cd ../backend && mvn spring-boot:run
# Full app at http://localhost:8080
```

The backend serves the compiled SPA, REST API (`/api`), and WebSocket (`/ws`) on one port.

---

## Configuration

```env
# Zerodha Kite Connect — from developers.kite.trade
KITE_API_KEY=your_kite_api_key
KITE_API_SECRET=your_kite_api_secret
KITE_REDIRECT_URL=http://localhost:8080/api/broker/callback

# JWT signing key: openssl rand -base64 32
APP_JWT_SECRET=

# AES-GCM key for encrypting the broker token at rest: openssl rand -base64 32
APP_CRYPTO_KEY=

# Dashboard login
APP_ADMIN_USERNAME=admin
APP_ADMIN_PASSWORD=your_password_here

# paper = simulated fills (default) | live = real orders
SPRING_PROFILES_ACTIVE=live
```

Trading parameters — lot size (65), strike step, session times (09:20–15:05), risk levels — are in [`backend/src/main/resources/application.yml`](backend/src/main/resources/application.yml).

---

## Strategies

### First Candle Breakout (FCB)

Records the 09:15–09:20 opening candle, then waits for a 5-minute close that breaks the opening range with trend confirmation.

- **BUY CE** — close breaks first-candle high + EMA9 > EMA15 + price > VWAP + ATR ≥ filter
- **BUY PE** — close breaks first-candle low + EMA9 < EMA15 + price < VWAP + ATR ≥ filter

### Supertrend Flip

Computes a Supertrend indicator (Wilder ATR) on live 5-minute candles. Fires when the close crosses the Supertrend band.

- **BUY CE** — bullish flip (close crosses above Supertrend line)
- **BUY PE** — bearish flip (close crosses below Supertrend line)

Config is **editable live** from the Settings page. Changing ATR period or multiplier restarts the warm-up. Changing only strike offset takes effect immediately.

Default config: ATR period 10 · multiplier 3.0 · strike offset 0 (ATM)

---

## Risk Management

| Stage | Trigger | Stop-loss |
|-------|---------|-----------|
| INITIAL | Entry placed | Entry − 25 pts |
| BREAKEVEN | +25 pts (Target 1) | Moved to entry |
| LOCKED | +50 pts (Target 2) | Entry + 30 pts |
| TRAILING | Beyond Target 2 | Trails +25 pts per +25 pts gain |

- Maximum 5 trades per day (configurable)
- Force-exit all positions at 15:05 IST
- All positions are MIS (intraday, auto-squared by broker at 15:20 if missed)

---

## Broker Authentication

1. Go to **Settings → Kite Connect** in the dashboard
2. Click **Login with Kite** → Zerodha OAuth flow
3. After approval the access token is returned to the callback URL and stored AES-GCM encrypted

Kite access tokens expire daily. Re-authenticate each morning before the session window opens.

---

## Important Notes

### IP Whitelist

Since **1 April 2026**, SEBI mandates that all Kite Connect order-placement calls originate from a registered static IP. Calls from a dynamic or unregistered IP are rejected with `PermissionException (HTTP 403)` **before** the order reaches the exchange — it will not appear in the Kite order book.

Register your server's outbound IP at **developers.kite.trade → Profile → IP Whitelist**.

> Zerodha allows **only one IP change per calendar week**. A static IP (ISP add-on, cloud VM) is strongly recommended for live trading. Oracle Cloud Always Free tier provides a permanent static IP at no cost.

### NFO Order Type

Zerodha blocks pure MARKET orders in the NFO (options) segment. The platform automatically places **aggressive LIMIT orders**: 2% above the live LTP for buys, 2% below for sells, rounded to the ₹0.05 NFO tick size. In a liquid Nifty options market these fill in milliseconds.

### Paper vs Live Profile

| | `paper` | `live` (default) |
|---|---|---|
| Orders | Simulated instant fills | Real orders via Kite Connect |
| Market data | Demo OHLC feed | Kite WebSocket ticker |
| Kite credentials | Not required | Required |

---

## All Times Are IST

The codebase enforces `Asia/Kolkata` throughout. `TradingClock` is used everywhere; `LocalTime.now()` is never called directly.

---

## Security Notes

- All secrets come from environment variables; nothing sensitive is in `application.yml`
- The Kite broker access token is AES-GCM encrypted at rest (key = `APP_CRYPTO_KEY`)
- JWT sessions expire after 12 hours
- `.env` and `backend/data/` are gitignored — never commit them

---

## Docs

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — module contracts, layering rules, event catalogue
- [`docs/API.md`](docs/API.md) — REST endpoint reference
- [`docs/WEBSOCKET.md`](docs/WEBSOCKET.md) — STOMP topics and payload schemas
