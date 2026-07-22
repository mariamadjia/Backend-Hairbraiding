ALTER TABLE service_items ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE service_items ADD COLUMN IF NOT EXISTS display_order INTEGER NOT NULL DEFAULT 0;
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS selected_texture VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_service_item_active_order
    ON service_items(active, display_order, id);

ALTER TABLE length_options DROP COLUMN IF EXISTS duration;
