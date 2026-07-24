ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS payment_authorization_expires_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_appointments_payment_authorization_expires
    ON appointments (payment_authorization_expires_at)
    WHERE payment_status = 'AUTHORIZED';
