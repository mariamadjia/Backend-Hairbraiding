-- NO_SHOW was added to AppointmentStatus after the original PostgreSQL
-- status check constraint was created. Replace only checks that validate the
-- appointments.status column so existing deployments accept the new value.
DO $$
DECLARE
    constraint_record RECORD;
BEGIN
    FOR constraint_record IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE n.nspname = current_schema()
          AND t.relname = 'appointments'
          AND c.contype = 'c'
          AND pg_get_constraintdef(c.oid) ~* '\mstatus\M'
    LOOP
        EXECUTE format(
            'ALTER TABLE appointments DROP CONSTRAINT %I',
            constraint_record.conname
        );
    END LOOP;
END
$$;

ALTER TABLE appointments
    ADD CONSTRAINT appointments_status_check
    CHECK (status IN (
        'PENDING',
        'APPROVAL_PENDING_CAPTURE',
        'APPROVED',
        'DENIED',
        'CANCELLED',
        'COMPLETED',
        'NO_SHOW'
    ));
