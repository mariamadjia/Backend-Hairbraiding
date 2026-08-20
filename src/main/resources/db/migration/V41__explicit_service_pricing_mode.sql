ALTER TABLE service_items
    ADD COLUMN IF NOT EXISTS pricing_mode VARCHAR(16) NOT NULL DEFAULT 'FIXED';

UPDATE service_items service
SET pricing_mode = CASE
    WHEN EXISTS (
        SELECT 1
        FROM length_options length_option
        WHERE length_option.service_item_id = service.id
    ) THEN 'BY_LENGTH'
    ELSE 'FIXED'
END;

ALTER TABLE service_items
    DROP CONSTRAINT IF EXISTS chk_service_item_pricing_mode;

ALTER TABLE service_items
    ADD CONSTRAINT chk_service_item_pricing_mode
    CHECK (pricing_mode IN ('FIXED', 'BY_LENGTH'));
