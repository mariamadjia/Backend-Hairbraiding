ALTER TABLE notification_outbox
    ADD COLUMN IF NOT EXISTS event_key VARCHAR(64),
    ADD COLUMN IF NOT EXISTS delivery_key VARCHAR(100),
    ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMP;

UPDATE notification_outbox SET event_key = 'legacy-' || id WHERE event_key IS NULL;
UPDATE notification_outbox SET delivery_key = event_key || ':' || channel WHERE delivery_key IS NULL;

ALTER TABLE notification_outbox
    ALTER COLUMN event_key SET NOT NULL,
    ALTER COLUMN delivery_key SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_outbox_delivery_key
    ON notification_outbox(delivery_key);

CREATE INDEX IF NOT EXISTS idx_notification_outbox_claim_recovery
    ON notification_outbox(status, claimed_at);
