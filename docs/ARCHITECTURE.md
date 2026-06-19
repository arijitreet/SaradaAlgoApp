# Architecture

## Style

**Modular monolith** with Clean Architecture + DDD inside each module. Modules talk through
**domain events** (`common.events.DomainEvents`) and **ports** (interfaces), never through
each other's repositories or infrastructure. Any module can be lifted into its own service
without touching the others.

```
┌────────────┐  ticks   ┌─────────────┐  candles  ┌──────────┐  signals
│   broker   │ ───────▶ │ marketdata  │ ────────▶ │ strategy │ ────────┐
│ Kite / sim │          │ aggregator  │           │  engine  │         │
└────────────┘          └─────────────┘           └──────────┘         ▼
      ▲                                                          ┌──────────┐
      │  orders                       approved + contract picked │  orders  │◀── risk gate
      └──────────────────────────────────────────────────────────┤ orchestr.│
                                                                 └────┬─────┘
                                                                      │ EntryFilled
                                                            ┌─────────▼─────────┐
                                                            │     positions     │
                                                            │ monitor + P&L     │
                                                            └─────────┬─────────┘
                                                      PositionClosed  │
                                              ┌───────────────────────┼───────────┐
                                              ▼                       ▼           ▼
                                         analytics               history     WebSocket UI
```

## Layering inside a module

| Layer         | Contents                                            | Depends on        |
|---------------|------------------------------------------------------|-------------------|
| `domain`      | entities, value objects, ports, pure policies        | `common` only     |
| `application` | use-cases, orchestration, event listeners            | domain, ports     |
| `infra`       | JPA repositories, broker SDK adapters                | domain            |
| `api`         | REST controllers (thin, no business logic)           | application       |

`common` is the shared kernel: market primitives (`Tick`, `Candle`, `TradeSignal`),
domain events, the trading clock, crypto, audit, and the WS publisher.

## Key ports (extension points)

| Port | Purpose | Implementations |
|------|---------|-----------------|
| `BrokerGateway` | order execution | `KiteBrokerGateway` (live), `PaperBrokerGateway` (paper) |
| `TradingStrategy` | signal generation SPI | `FirstCandleBreakoutStrategy` (more = more beans) |
| `TradeStatsPort` | risk's view of executed trades | `PositionService` |
| Market feed | tick source | `KiteTickerManager` (real), `SimulatedTickFeed` (demo) |

## Future capabilities — how they slot in

- **Multi-symbol** — `CandleAggregator.track()` is per-token; strategies declare their
  `underlying()`. Add a second strategy bean bound to BANKNIFTY and subscribe its token.
- **Multi-strategy** — `StrategyEngine` already fans out to `List<TradingStrategy>`.
  Per-strategy risk budgets would extend `RiskManager` with a strategy dimension.
- **Multi-broker** — implement `BrokerGateway` + a feed manager for the new broker;
  selection becomes configuration.
- **Paper trading** — shipped; the `paper` profile swaps the gateway, everything else
  is byte-identical.
- **Backtesting** — replay historical candles as `CandleClosed` events into the same
  engine with a virtual `TradingClock` and the paper gateway; persist results to the
  same schema for the same analytics.

## Concurrency model

- Ticks arrive on the Kite ticker thread → published as Spring events (synchronous,
  cheap: cache update, candle fold, position-stop check).
- Heavy/slow work (entry pipeline, audit, analytics, force-exit) runs on `appExecutor`
  via `@Async` so the feed thread is never blocked.
- Double-exit races are guarded by an in-flight set in `PositionService`; stops are
  monotonic by construction in `TrailingStopPolicy`.

## State & resilience

- Session running-flag lives in the `app_settings` table (embedded H2) → restarts
  mid-session resume safely (self-expiring across trading days, no TTL needed).
- Kite access token is **AES-256-GCM encrypted** at rest; key from env.
- Ticker auto-reconnects (SDK retries) + a 30s watchdog forces a fresh connect during
  market hours; subscriptions replay automatically on every reconnect.
- Candles are idempotently persisted (unique key on token/interval/open-time).
- Force-exit is a server-side cron — it fires even if the UI is closed.
