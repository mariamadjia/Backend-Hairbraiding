DELETE FROM homepage_settings
WHERE id NOT IN (SELECT MIN(id) FROM homepage_settings);

UPDATE homepage_settings SET version = 0 WHERE version IS NULL;

ALTER TABLE homepage_settings
    ALTER COLUMN version SET DEFAULT 0,
    ALTER COLUMN version SET NOT NULL;

ALTER TABLE homepage_settings
    ADD COLUMN IF NOT EXISTS singleton_key SMALLINT NOT NULL DEFAULT 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_homepage_settings_singleton
    ON homepage_settings(singleton_key);
