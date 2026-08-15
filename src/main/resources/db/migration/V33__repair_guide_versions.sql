-- Tables may already have been created by Hibernate before V32 ran. In that
-- case CREATE TABLE IF NOT EXISTS does not repair nullable version columns.
UPDATE guide_settings
SET version = 0
WHERE version IS NULL;

ALTER TABLE guide_settings
    ALTER COLUMN version SET DEFAULT 0,
    ALTER COLUMN version SET NOT NULL;

UPDATE size_guide_profiles
SET version = 0
WHERE version IS NULL;

ALTER TABLE size_guide_profiles
    ALTER COLUMN version SET DEFAULT 0,
    ALTER COLUMN version SET NOT NULL;
