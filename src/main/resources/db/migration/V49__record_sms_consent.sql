ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS sms_consent_accepted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS sms_consent_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS sms_consent_policy_version VARCHAR(50);
