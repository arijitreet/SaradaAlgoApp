-- ───────────────────────── users & auth ─────────────────────────
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(16)  NOT NULL DEFAULT 'ADMIN',
    created_at    TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT now()
);

-- Kite OAuth session; access token stored AES-GCM encrypted
CREATE TABLE broker_sessions (
    id                     BIGSERIAL PRIMARY KEY,
    broker                 VARCHAR(16)  NOT NULL DEFAULT 'ZERODHA',
    kite_user_id           VARCHAR(32),
    access_token_encrypted TEXT,
    public_token_encrypted TEXT,
    login_time             TIMESTAMP WITH TIME ZONE,
    expires_at             TIMESTAMP WITH TIME ZONE,
    active                 BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT now()
);

-- ───────────────────────── market data ──────────────────────────
CREATE TABLE instruments (
    instrument_token BIGINT       PRIMARY KEY,
    tradingsymbol    VARCHAR(64)  NOT NULL,
    name             VARCHAR(64),
    exchange         VARCHAR(8)   NOT NULL,
    segment          VARCHAR(16),
    instrument_type  VARCHAR(8),               -- CE / PE / FUT / EQ
    strike           NUMERIC(12,2),
    expiry           DATE,
    lot_size         INT,
    tick_size        NUMERIC(8,2),
    refreshed_at     TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT now()
);
CREATE INDEX idx_instruments_lookup
    ON instruments (name, instrument_type, expiry, strike);

CREATE TABLE candles (
    id               BIGSERIAL PRIMARY KEY,
    instrument_token BIGINT       NOT NULL,
    symbol           VARCHAR(64)  NOT NULL,
    interval_minutes INT          NOT NULL,
    open_time        TIMESTAMP WITH TIME ZONE  NOT NULL,
    open             NUMERIC(12,2) NOT NULL,
    high             NUMERIC(12,2) NOT NULL,
    low              NUMERIC(12,2) NOT NULL,
    close            NUMERIC(12,2) NOT NULL,
    volume           BIGINT       NOT NULL DEFAULT 0,
    UNIQUE (instrument_token, interval_minutes, open_time)
);
CREATE INDEX idx_candles_token_time ON candles (instrument_token, open_time DESC);

-- ───────────────────────── strategy ─────────────────────────────
CREATE TABLE signals (
    id             BIGSERIAL PRIMARY KEY,
    strategy_id    VARCHAR(64)  NOT NULL,
    trading_day    DATE         NOT NULL,
    signal_type    VARCHAR(8)   NOT NULL,          -- BUY_CE / BUY_PE
    underlying     VARCHAR(32)  NOT NULL,
    trigger_price  NUMERIC(12,2) NOT NULL,
    ema_fast       NUMERIC(12,2),
    ema_slow       NUMERIC(12,2),
    vwap           NUMERIC(12,2),
    atr            NUMERIC(12,2),
    reason         TEXT,
    accepted       BOOLEAN      NOT NULL,
    reject_reason  TEXT,
    created_at     TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT now()
);
CREATE INDEX idx_signals_day ON signals (trading_day);

-- ───────────────────────── orders ───────────────────────────────
CREATE TABLE orders (
    id               BIGSERIAL PRIMARY KEY,
    broker_order_id  VARCHAR(64),
    trading_day      DATE          NOT NULL,
    instrument_token BIGINT        NOT NULL,
    tradingsymbol    VARCHAR(64)   NOT NULL,
    exchange         VARCHAR(8)    NOT NULL,
    side             VARCHAR(4)    NOT NULL,        -- BUY / SELL
    order_type       VARCHAR(8)    NOT NULL,        -- MARKET / LIMIT
    quantity         INT           NOT NULL,
    price            NUMERIC(12,2),
    avg_fill_price   NUMERIC(12,2),
    status           VARCHAR(16)   NOT NULL,        -- NEW/SUBMITTED/FILLED/REJECTED/CANCELLED
    status_message   TEXT,
    mode             VARCHAR(8)    NOT NULL,        -- PAPER / LIVE
    placed_at        TIMESTAMP WITH TIME ZONE   NOT NULL DEFAULT now(),
    filled_at        TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_orders_day ON orders (trading_day);

-- ───────────────────────── positions ────────────────────────────
CREATE TABLE positions (
    id               BIGSERIAL PRIMARY KEY,
    trading_day      DATE          NOT NULL,
    strategy_id      VARCHAR(64)   NOT NULL,
    signal_id        BIGINT        REFERENCES signals(id),
    entry_order_id   BIGINT        REFERENCES orders(id),
    exit_order_id    BIGINT        REFERENCES orders(id),
    instrument_token BIGINT        NOT NULL,
    tradingsymbol    VARCHAR(64)   NOT NULL,
    option_type      VARCHAR(2)    NOT NULL,        -- CE / PE
    strike           NUMERIC(12,2) NOT NULL,
    expiry           DATE          NOT NULL,
    quantity         INT           NOT NULL,
    entry_price      NUMERIC(12,2) NOT NULL,
    stop_loss        NUMERIC(12,2) NOT NULL,
    target1          NUMERIC(12,2) NOT NULL,
    target2          NUMERIC(12,2) NOT NULL,
    risk_stage       VARCHAR(16)   NOT NULL DEFAULT 'INITIAL', -- INITIAL/BREAKEVEN/LOCKED/TRAILING
    exit_price       NUMERIC(12,2),
    exit_reason      VARCHAR(32),                  -- STOP_LOSS/TRAIL_STOP/FORCE_EXIT/MANUAL/SESSION_END
    realized_pnl     NUMERIC(14,2),
    status           VARCHAR(12)   NOT NULL,        -- OPEN / CLOSED
    opened_at        TIMESTAMP WITH TIME ZONE   NOT NULL DEFAULT now(),
    closed_at        TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_positions_day ON positions (trading_day);
CREATE INDEX idx_positions_status ON positions (status);

-- ───────────────────────── analytics ────────────────────────────
CREATE TABLE daily_stats (
    trading_day    DATE PRIMARY KEY,
    trades         INT           NOT NULL DEFAULT 0,
    wins           INT           NOT NULL DEFAULT 0,
    losses         INT           NOT NULL DEFAULT 0,
    gross_pnl      NUMERIC(14,2) NOT NULL DEFAULT 0,
    max_drawdown   NUMERIC(14,2) NOT NULL DEFAULT 0,
    best_trade     NUMERIC(14,2) NOT NULL DEFAULT 0,
    worst_trade    NUMERIC(14,2) NOT NULL DEFAULT 0,
    updated_at     TIMESTAMP WITH TIME ZONE   NOT NULL DEFAULT now()
);

-- ───────────────────────── audit ────────────────────────────────
CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    category    VARCHAR(24)  NOT NULL,   -- AUTH/BROKER/SESSION/SIGNAL/ORDER/POSITION/RISK/SYSTEM
    action      VARCHAR(64)  NOT NULL,
    detail      TEXT,
    actor       VARCHAR(32)  NOT NULL DEFAULT 'SYSTEM',
    created_at  TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_created ON audit_logs (created_at DESC);

-- ───────────────────────── settings ─────────────────────────────
CREATE TABLE app_settings (
    setting_key   VARCHAR(64) PRIMARY KEY,
    setting_value TEXT        NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
