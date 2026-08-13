CREATE TABLE IF NOT EXISTS appointment_events (
    id BIGSERIAL PRIMARY KEY,
    appointment_id BIGINT NOT NULL REFERENCES appointments(id) ON DELETE CASCADE,
    actor_admin_id BIGINT REFERENCES admin(id) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    appointment_status VARCHAR(30) NOT NULL,
    payment_status VARCHAR(30),
    reason VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_appointment_events_appointment
    ON appointment_events (appointment_id, created_at);

CREATE TABLE IF NOT EXISTS notification_outbox (
    id BIGSERIAL PRIMARY KEY,
    appointment_id BIGINT NOT NULL REFERENCES appointments(id) ON DELETE CASCADE,
    channel VARCHAR(10) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(255),
    body VARCHAR(2000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notification_outbox_delivery
    ON notification_outbox (status, next_attempt_at);
