ALTER TABLE service_items
    ADD COLUMN IF NOT EXISTS duration_minutes INTEGER NOT NULL DEFAULT 60;

UPDATE service_items SET duration_minutes = 60
WHERE duration_minutes IS NULL OR duration_minutes < 15;

ALTER TABLE business_hours
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE blocked_time_slots
    ADD COLUMN IF NOT EXISTS recurrence_end_date DATE;

UPDATE appointments
SET duration_minutes = COALESCE(duration_minutes, 60),
    appointment_end_date_time = COALESCE(appointment_end_date_time, appointment_date_time + INTERVAL '60 minutes')
WHERE appointment_end_date_time IS NULL;
