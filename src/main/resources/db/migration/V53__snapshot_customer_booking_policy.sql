ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS booking_policy_snapshot TEXT;

