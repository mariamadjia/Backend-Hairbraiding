ALTER TABLE appointment_settings
    ADD COLUMN IF NOT EXISTS default_deposit_cents BIGINT NOT NULL DEFAULT 5000;

ALTER TABLE service_items
    ADD COLUMN IF NOT EXISTS deposit_override_cents BIGINT;

ALTER TABLE appointment_settings
    DROP CONSTRAINT IF EXISTS chk_default_deposit_positive;
ALTER TABLE appointment_settings
    ADD CONSTRAINT chk_default_deposit_positive CHECK (default_deposit_cents > 0);

ALTER TABLE service_items
    DROP CONSTRAINT IF EXISTS chk_deposit_override_positive;
ALTER TABLE service_items
    ADD CONSTRAINT chk_deposit_override_positive CHECK (deposit_override_cents IS NULL OR deposit_override_cents > 0);

CREATE TABLE IF NOT EXISTS pricing_history (
    id BIGSERIAL PRIMARY KEY,
    service_item_id BIGINT,
    service_name VARCHAR(120) NOT NULL,
    action VARCHAR(30) NOT NULL,
    summary VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pricing_history_service
        FOREIGN KEY (service_item_id) REFERENCES service_items(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_pricing_history_created_at
    ON pricing_history(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pricing_history_service
    ON pricing_history(service_item_id);
