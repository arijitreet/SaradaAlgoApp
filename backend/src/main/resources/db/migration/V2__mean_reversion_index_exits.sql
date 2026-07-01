-- Mean Reversion exits are evaluated against the underlying INDEX level
-- (BB-width stop and mid-BB target), not the option premium. These nullable
-- columns hold the index levels; they are null for every other strategy, which
-- continues to use the premium-based trailing-stop ladder.
ALTER TABLE positions ADD COLUMN index_stop_loss NUMERIC(12,2);
ALTER TABLE positions ADD COLUMN index_target    NUMERIC(12,2);
