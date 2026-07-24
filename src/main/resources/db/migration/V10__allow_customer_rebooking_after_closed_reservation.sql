DO $$
BEGIN
    IF to_regclass('public.appointments') IS NOT NULL
       AND EXISTS (
           SELECT 1
           FROM information_schema.columns
           WHERE table_schema = 'public'
             AND table_name = 'appointments'
             AND column_name = 'customer_id'
       ) THEN
        ALTER TABLE appointments
            DROP CONSTRAINT IF EXISTS idx_appointment_customer_datetime;
        DROP INDEX IF EXISTS idx_appointment_customer_datetime;
        CREATE INDEX idx_appointment_customer_datetime
            ON appointments (customer_id, appointment_date_time);
    END IF;
END $$;
