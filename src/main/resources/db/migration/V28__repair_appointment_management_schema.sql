-- Repair production databases that were baselined or partially migrated before
-- appointment management and booking add-ons were introduced. Every statement
-- is safe to run when the expected schema already exists.

CREATE TABLE IF NOT EXISTS booking_add_ons (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    name VARCHAR(120) NOT NULL,
    description TEXT,
    pricing_mode VARCHAR(30) NOT NULL DEFAULT 'FIXED',
    price_cents BIGINT NOT NULL DEFAULT 0,
    deposit_behavior VARCHAR(30) NOT NULL DEFAULT 'NO_CHANGE',
    deposit_adjustment_cents BIGINT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS appointment_add_ons (
    id BIGSERIAL PRIMARY KEY,
    appointment_id BIGINT NOT NULL REFERENCES appointments(id) ON DELETE CASCADE,
    add_on_id BIGINT REFERENCES booking_add_ons(id) ON DELETE SET NULL,
    add_on_name VARCHAR(120) NOT NULL,
    pricing_mode VARCHAR(30) NOT NULL,
    advertised_price_cents BIGINT NOT NULL DEFAULT 0,
    charged_price_cents BIGINT NOT NULL DEFAULT 0,
    display_order INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_appointment_add_ons_appointment
    ON appointment_add_ons(appointment_id, display_order, id);

-- A short-lived release wrote this legacy value. Hibernate cannot hydrate an
-- Appointment until it is converted back to the current state model.
UPDATE appointments
SET status = 'PENDING'
WHERE status = 'APPROVAL_PENDING_CAPTURE';

-- Repair nullable legacy values before enum/DTO mapping.
UPDATE appointments SET status = 'PENDING' WHERE status IS NULL;
UPDATE appointments SET payment_status = 'PENDING' WHERE payment_status IS NULL;

