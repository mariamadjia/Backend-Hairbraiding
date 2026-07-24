-- Repair deployments where the carousel-order mapping reached production
-- without the corresponding collection-table column.
ALTER TABLE category_flipping_images
    ADD COLUMN IF NOT EXISTS display_order INTEGER;

WITH ordered_images AS (
    SELECT
        ctid,
        ROW_NUMBER() OVER (
            PARTITION BY category_id
            ORDER BY COALESCE(display_order, 2147483647), ctid
        ) - 1 AS position
    FROM category_flipping_images
)
UPDATE category_flipping_images AS images
SET display_order = ordered_images.position
FROM ordered_images
WHERE images.ctid = ordered_images.ctid;

ALTER TABLE category_flipping_images
    ALTER COLUMN display_order SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_category_flipping_images_order
    ON category_flipping_images (category_id, display_order);
