-- The outbox worker claims rows by moving them from PENDING to PROCESSING.
-- Databases originally created from the enum mapping may still have the old
-- Hibernate-generated constraint, which rejects PROCESSING and prevents every
-- notification from being attempted.
ALTER TABLE notification_outbox
    DROP CONSTRAINT IF EXISTS notification_outbox_status_check;

ALTER TABLE notification_outbox
    ADD CONSTRAINT notification_outbox_status_check
    CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED'));
