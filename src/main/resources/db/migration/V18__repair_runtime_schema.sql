-- V18: Repair missing columns that caused 500s on pricing and appointment endpoints.
-- All ADD COLUMN statements use IF NOT EXISTS so they are safe to rerun.

-- Appointment settings (default deposit, version for optimistic locking)
ALTER TABLE appointment_settings
    ADD COLUMN IF NOT EXISTS default_deposit_cents BIGINT NOT NULL DEFAULT 5000,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS buffer_time_between_appointments INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS timezone VARCHAR(50) DEFAULT 'America/Chicago',
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_by BIGINT;

-- Appointments (version, payment lifecycle and notification columns)
ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS payment_intent_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS deposit_amount BIGINT,
    ADD COLUMN IF NOT EXISTS payment_captured_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS payment_authorization_expires_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS payment_pending_expires_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS payment_method_last4 VARCHAR(4),
    ADD COLUMN IF NOT EXISTS payment_method_brand VARCHAR(50),
    ADD COLUMN IF NOT EXISTS notification_status VARCHAR(30),
    ADD COLUMN IF NOT EXISTS notification_last_attempt_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS selected_service VARCHAR(100),
    ADD COLUMN IF NOT EXISTS selected_size VARCHAR(255),
    ADD COLUMN IF NOT EXISTS selected_length VARCHAR(255),
    ADD COLUMN IF NOT EXISTS selected_foundation VARCHAR(20),
    ADD COLUMN IF NOT EXISTS selected_texture VARCHAR(100),
    ADD COLUMN IF NOT EXISTS appointment_end_date_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS duration_minutes INTEGER,
    ADD COLUMN IF NOT EXISTS admin_notes VARCHAR(500),
    ADD COLUMN IF NOT EXISTS approved_by BIGINT,
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS price VARCHAR(255);

-- Service items
ALTER TABLE service_items
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS deposit_override_cents BIGINT,
    ADD COLUMN IF NOT EXISTS foundation_choices_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS knotless_price_adjustment VARCHAR(255) NOT NULL DEFAULT '0';

-- Length options
ALTER TABLE length_options
    ADD COLUMN IF NOT EXISTS display_order INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS image_url VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS notes TEXT;

-- Pricing history
ALTER TABLE pricing_history
    ADD COLUMN IF NOT EXISTS changed_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS source VARCHAR(40) NOT NULL DEFAULT 'SYSTEM',
    ADD COLUMN IF NOT EXISTS batch_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS before_value TEXT,
    ADD COLUMN IF NOT EXISTS after_value TEXT;

-- Helper indexes for the new appointment columns
CREATE INDEX IF NOT EXISTS idx_appointments_payment_pending_expires
    ON appointments (payment_pending_expires_at)
    WHERE status = 'PENDING' AND payment_status = 'PENDING';
