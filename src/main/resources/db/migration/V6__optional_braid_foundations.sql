ALTER TABLE service_items
    ADD COLUMN IF NOT EXISTS foundation_choices_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS knotless_price_adjustment VARCHAR(255) NOT NULL DEFAULT '0';

ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS selected_foundation VARCHAR(20);
