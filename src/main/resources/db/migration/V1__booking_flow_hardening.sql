-- Forward-only booking schema additions. Existing rows are intentionally not backfilled.
DO $$
BEGIN
    IF to_regclass('public.appointments') IS NOT NULL THEN
        ALTER TABLE appointments ADD COLUMN IF NOT EXISTS appointment_end_date_time timestamp;
        ALTER TABLE appointments ADD COLUMN IF NOT EXISTS duration_minutes integer;
        CREATE INDEX IF NOT EXISTS idx_appointment_time_range
            ON appointments (appointment_date_time, appointment_end_date_time);
    END IF;
END $$;
