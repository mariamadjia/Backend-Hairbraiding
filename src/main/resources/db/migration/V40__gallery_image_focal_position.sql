ALTER TABLE gallery_images
    ADD COLUMN IF NOT EXISTS focal_position VARCHAR(16) NOT NULL DEFAULT 'center';

ALTER TABLE gallery_images
    DROP CONSTRAINT IF EXISTS chk_gallery_image_focal_position;

ALTER TABLE gallery_images
    ADD CONSTRAINT chk_gallery_image_focal_position
    CHECK (focal_position IN ('top', 'center', 'bottom'));
