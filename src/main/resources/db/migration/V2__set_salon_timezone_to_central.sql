-- The salon operates in San Antonio, Texas. Update only the previous application
-- default (and missing values), preserving any deliberately customized timezone.
DO $$
BEGIN
    IF to_regclass('public.appointment_settings') IS NOT NULL THEN
        UPDATE appointment_settings
        SET timezone = 'America/Chicago'
        WHERE timezone IS NULL
           OR BTRIM(timezone) = ''
           OR timezone = 'America/Los_Angeles';
    END IF;
END $$;
