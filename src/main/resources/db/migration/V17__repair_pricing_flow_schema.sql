-- Production repair migration.
-- These statements intentionally repeat V16 with IF NOT EXISTS because some
-- environments recorded version 16 without receiving every pricing column.

ALTER TABLE service_items
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE appointment_settings
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE length_options
    ADD COLUMN IF NOT EXISTS display_order INTEGER NOT NULL DEFAULT 0;

WITH ordered AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY service_item_id ORDER BY id) - 1 AS new_order
    FROM length_options
)
UPDATE length_options target
SET display_order = ordered.new_order
FROM ordered
WHERE target.id = ordered.id;

ALTER TABLE pricing_history
    ADD COLUMN IF NOT EXISTS changed_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS source VARCHAR(40) NOT NULL DEFAULT 'SYSTEM',
    ADD COLUMN IF NOT EXISTS batch_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS before_value TEXT,
    ADD COLUMN IF NOT EXISTS after_value TEXT;

CREATE INDEX IF NOT EXISTS idx_length_options_service_order
    ON length_options(service_item_id, display_order, id);

CREATE INDEX IF NOT EXISTS idx_pricing_history_batch
    ON pricing_history(batch_id);
