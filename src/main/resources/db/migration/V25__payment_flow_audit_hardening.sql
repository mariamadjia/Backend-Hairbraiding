ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS amount_authorized BIGINT,
    ADD COLUMN IF NOT EXISTS amount_captured BIGINT,
    ADD COLUMN IF NOT EXISTS deposit_policy_version VARCHAR(50),
    ADD COLUMN IF NOT EXISTS deposit_policy_accepted_at TIMESTAMP;

UPDATE appointments
SET amount_authorized = deposit_amount
WHERE payment_status IN ('AUTHORIZED', 'CAPTURED') AND amount_authorized IS NULL;

UPDATE appointments
SET amount_captured = deposit_amount
WHERE payment_status = 'CAPTURED' AND amount_captured IS NULL;

CREATE TABLE IF NOT EXISTS stripe_webhook_events (
    id BIGSERIAL PRIMARY KEY,
    stripe_event_id VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 1,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stripe_webhook_events_status
    ON stripe_webhook_events (status, updated_at);

CREATE UNIQUE INDEX IF NOT EXISTS uk_appointments_payment_intent_id
    ON appointments (payment_intent_id)
    WHERE payment_intent_id IS NOT NULL;
