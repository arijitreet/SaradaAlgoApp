# Sarada Trading Platform — repo guide

Automated Nifty options trading platform. Java 17 / Spring Boot 3.2 backend +
React 18 / Vite / TypeScript frontend. Full docs in `docs/`.

## Layout

- `backend/` — Maven, modular monolith under `com.sarada.trading.{common,auth,broker,marketdata,strategy,risk,orders,positions,analytics,history}`. Each module: `domain` / `application` / `infra` / `api`.
- `frontend/` — Vite app: `src/{components,pages,stores,hooks,lib,types}`.
- Flyway migrations: `backend/src/main/resources/db/migration`.
- Runtime config: `backend/src/main/resources/application.yml` + env vars (`.env.example`).

## Commands

- Backend build: `cd backend && mvn -q compile` · tests: `mvn test`
- Frontend: `cd frontend && npm install && npm run dev` (proxies /api + /ws → :8080)
- Full app, single port: `npm run build` (in `frontend/`) then `cd backend && mvn spring-boot:run`
  → `http://localhost:8080` (serves API, WS and the built SPA). No Docker, Postgres or
  Redis — embedded H2 file DB at `backend/data/`, `.env`/`backend/.env` auto-loaded.

## Rules

- Modules communicate only via `common.events.DomainEvents` and ports — never import
  another module's `infra` or call its repositories.
- All times are IST (`Asia/Kolkata`); use `common.time.TradingClock`, never `LocalTime.now()`.
- Money/prices are `BigDecimal`. Never `double` in domain logic.
- Secrets only via env vars; broker tokens go through `TokenEncryptor`.
- Spring profiles: `paper` (default, simulated fills + demo feed) / `live` (real Kite orders).
