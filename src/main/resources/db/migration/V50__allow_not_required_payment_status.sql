-- Existing PostgreSQL deployments can retain Hibernate's original enum check
-- constraint. Replace only checks on appointments.payment_status so zero-deposit
-- bookings can persist the NOT_REQUIRED state.
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
          AND pg_get_constraintdef(c.oid) ~* '\mpayment_status\M'
    LOOP
        EXECUTE format(
            'ALTER TABLE appointments DROP CONSTRAINT %I',
            constraint_record.conname
        );
    END LOOP;
END
$$;

ALTER TABLE appointments
    ADD CONSTRAINT appointments_payment_status_check
    CHECK (payment_status IN (
        'PENDING',
        'NOT_REQUIRED',
        'AUTHORIZED',
        'CAPTURED',
        'CAPTURE_FAILED',
        'CANCELLATION_FAILED',
        'CANCELLED',
        'FAILED'
    ));
