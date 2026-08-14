CREATE TABLE IF NOT EXISTS guide_settings (
    id BIGINT PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    length_guide_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    size_guide_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    length_guide_image_url VARCHAR(2000)
);

INSERT INTO guide_settings(id, version, length_guide_enabled, size_guide_enabled)
VALUES (1, 0, FALSE, FALSE) ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS size_guide_profiles (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    guide_key VARCHAR(40) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    image_url VARCHAR(2000),
    display_order INTEGER NOT NULL,
    CONSTRAINT uq_size_guide_key UNIQUE(guide_key)
);

INSERT INTO size_guide_profiles(guide_key, display_name, display_order) VALUES
('xsmall','XSmall',0), ('small','Small',1), ('smedium','Smedium',2),
('medium','Medium',3), ('large','Large',4), ('jumbo','Jumbo',5)
ON CONFLICT (guide_key) DO NOTHING;

ALTER TABLE service_items ADD COLUMN IF NOT EXISTS size_guide_key VARCHAR(40);
UPDATE service_items SET size_guide_key = CASE
    WHEN lower(regexp_replace(name, '[^a-zA-Z]', '', 'g')) IN ('xsmall','small','smedium','medium','large','jumbo')
    THEN lower(regexp_replace(name, '[^a-zA-Z]', '', 'g')) ELSE NULL END
WHERE size_guide_key IS NULL;
