-- V27 may already be recorded in flyway_schema_history on production databases
-- whose physical schema was restored or partially migrated. Reassert the
-- duration/schedule columns under a new migration version.

ALTER TABLE service_items
    ADD COLUMN IF NOT EXISTS duration_minutes INTEGER;

UPDATE service_items
SET duration_minutes = 60
WHERE duration_minutes IS NULL OR duration_minutes < 15;

ALTER TABLE service_items
    ALTER COLUMN duration_minutes SET DEFAULT 60,
    ALTER COLUMN duration_minutes SET NOT NULL;

ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS duration_minutes INTEGER,
    ADD COLUMN IF NOT EXISTS appointment_end_date_time TIMESTAMP;

UPDATE appointments
SET duration_minutes = COALESCE(duration_minutes, 60),
    appointment_end_date_time = COALESCE(
        appointment_end_date_time,
        appointment_date_time + (COALESCE(duration_minutes, 60) * INTERVAL '1 minute')
    )
WHERE duration_minutes IS NULL OR appointment_end_date_time IS NULL;

ALTER TABLE business_hours
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE blocked_time_slots
    ADD COLUMN IF NOT EXISTS recurrence_end_date DATE;

