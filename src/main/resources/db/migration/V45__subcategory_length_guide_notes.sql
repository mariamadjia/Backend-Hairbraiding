ALTER TABLE subcategories
    ADD COLUMN IF NOT EXISTS length_guide_note_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS length_guide_note VARCHAR(500);
