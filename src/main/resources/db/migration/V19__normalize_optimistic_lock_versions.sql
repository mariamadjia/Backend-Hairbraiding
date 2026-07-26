-- Existing production databases may already have nullable version columns.
-- ADD COLUMN IF NOT EXISTS does not repair the definition when the column
-- exists, so normalize stored rows before enforcing the Hibernate contract.

UPDATE service_items SET version = 0 WHERE version IS NULL;
ALTER TABLE service_items ALTER COLUMN version SET DEFAULT 0;
ALTER TABLE service_items ALTER COLUMN version SET NOT NULL;

UPDATE appointment_settings SET version = 0 WHERE version IS NULL;
ALTER TABLE appointment_settings ALTER COLUMN version SET DEFAULT 0;
ALTER TABLE appointment_settings ALTER COLUMN version SET NOT NULL;

UPDATE appointments SET version = 0 WHERE version IS NULL;
ALTER TABLE appointments ALTER COLUMN version SET DEFAULT 0;
ALTER TABLE appointments ALTER COLUMN version SET NOT NULL;
