-- Older business-hours rows predate optimistic locking. A nullable version
-- causes Hibernate to throw while calculating the next version during save.
ALTER TABLE business_hours
    ADD COLUMN IF NOT EXISTS version BIGINT;

UPDATE business_hours
SET version = 0
WHERE version IS NULL;

ALTER TABLE business_hours
    ALTER COLUMN version SET DEFAULT 0,
    ALTER COLUMN version SET NOT NULL;

