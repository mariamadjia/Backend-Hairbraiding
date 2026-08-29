ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS booking_source VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    ADD COLUMN IF NOT EXISTS created_by_admin_id BIGINT REFERENCES admin(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS deposit_required BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS deposit_link_expires_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deposit_waived_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deposit_waived_by_admin_id BIGINT REFERENCES admin(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_appointments_owner_deposit_expiry
    ON appointments (booking_source, status, payment_status, deposit_link_expires_at);
