ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS notification_status VARCHAR(30);

ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS notification_last_attempt_at TIMESTAMP;
