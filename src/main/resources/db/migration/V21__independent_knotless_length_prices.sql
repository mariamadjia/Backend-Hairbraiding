ALTER TABLE service_items
    ADD COLUMN IF NOT EXISTS knotless_pricing_mode VARCHAR(20) NOT NULL DEFAULT 'ADJUSTMENT';

ALTER TABLE length_options
    ADD COLUMN IF NOT EXISTS knotless_price VARCHAR(255);

UPDATE service_items
SET knotless_pricing_mode = 'ADJUSTMENT'
WHERE knotless_pricing_mode IS NULL OR knotless_pricing_mode NOT IN ('ADJUSTMENT', 'SEPARATE');
