-- Stores signals that were rejected by Kite due to insufficient funds and are
-- eligible for a single retry once an existing position closes (freeing margin).
-- Restart-safe: the row survives app restarts; @PostConstruct expires stale rows.
CREATE TABLE pending_retry (
    id              BIGSERIAL PRIMARY KEY,
    strategy_id     VARCHAR(64)   NOT NULL,
    trading_day     DATE          NOT NULL,
    signal_type     VARCHAR(8)    NOT NULL,           -- BUY_CE / BUY_PE
    underlying      VARCHAR(32)   NOT NULL,
    trigger_price   NUMERIC(12,2) NOT NULL,
    strike_offset   INT           NOT NULL DEFAULT 0,
    index_stop_loss NUMERIC(12,2),
    index_target    NUMERIC(12,2),
    reject_reason   VARCHAR(512),
    status          VARCHAR(16)   NOT NULL DEFAULT 'WAITING',  -- WAITING / CONSUMED / EXPIRED
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX idx_pending_retry_status_day ON pending_retry (status, trading_day);
