-- Hibernate may have created these columns before Flyway saw V4. In that case
-- ADD COLUMN IF NOT EXISTS does not add the intended defaults/constraints.
ALTER TABLE service_items ADD COLUMN IF NOT EXISTS active BOOLEAN;
UPDATE service_items SET active = TRUE WHERE active IS NULL;
ALTER TABLE service_items ALTER COLUMN active SET DEFAULT TRUE;
ALTER TABLE service_items ALTER COLUMN active SET NOT NULL;

ALTER TABLE service_items ADD COLUMN IF NOT EXISTS display_order INTEGER;
UPDATE service_items SET display_order = 0 WHERE display_order IS NULL;
ALTER TABLE service_items ALTER COLUMN display_order SET DEFAULT 0;
ALTER TABLE service_items ALTER COLUMN display_order SET NOT NULL;

ALTER TABLE appointments ADD COLUMN IF NOT EXISTS selected_texture VARCHAR(100);
