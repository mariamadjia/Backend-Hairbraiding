ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS stripe_customer_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS stripe_payment_method_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS off_session_consent_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS off_session_consent_policy_version VARCHAR(50);

CREATE UNIQUE INDEX IF NOT EXISTS uq_customers_stripe_customer_id
    ON customers(stripe_customer_id) WHERE stripe_customer_id IS NOT NULL;

ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS off_session_consent_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS off_session_consent_policy_version VARCHAR(50),
    ADD COLUMN IF NOT EXISTS management_token_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS management_token_expires_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS self_service_change_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_self_service_change_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS rescheduled_from_datetime TIMESTAMP,
    ADD COLUMN IF NOT EXISTS cancelled_by_customer BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS no_show_marked_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS no_show_marked_by BIGINT REFERENCES admins(id);

CREATE TABLE IF NOT EXISTS appointment_no_show_fees (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    appointment_id BIGINT NOT NULL REFERENCES appointments(id),
    scheduled_service_price_cents BIGINT NOT NULL,
    fee_rate_percent INTEGER NOT NULL,
    total_fee_cents BIGINT NOT NULL,
    deposit_credit_cents BIGINT NOT NULL,
    amount_to_charge_cents BIGINT NOT NULL,
    fee_decision VARCHAR(20) NOT NULL,
    payment_status VARCHAR(20) NOT NULL,
    stripe_payment_intent_id VARCHAR(255),
    charge_attempt_count INTEGER NOT NULL DEFAULT 0,
    failure_message VARCHAR(1000),
    admin_note VARCHAR(500),
    marked_at TIMESTAMP NOT NULL,
    charge_attempted_at TIMESTAMP,
    paid_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_no_show_fee_appointment UNIQUE (appointment_id),
    CONSTRAINT ck_no_show_fee_rate CHECK (fee_rate_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_no_show_fee_amounts CHECK (
        scheduled_service_price_cents > 0 AND total_fee_cents >= 0
        AND deposit_credit_cents >= 0 AND amount_to_charge_cents >= 0
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_appointments_management_token_hash
    ON appointments(management_token_hash) WHERE management_token_hash IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_no_show_fee_payment_intent
    ON appointment_no_show_fees(stripe_payment_intent_id)
    WHERE stripe_payment_intent_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_no_show_fee_payment_status
    ON appointment_no_show_fees(payment_status, marked_at);
