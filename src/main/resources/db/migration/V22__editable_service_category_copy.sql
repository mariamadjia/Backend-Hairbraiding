ALTER TABLE categories
    ADD COLUMN IF NOT EXISTS service_tagline VARCHAR(255),
    ADD COLUMN IF NOT EXISTS service_description TEXT;
