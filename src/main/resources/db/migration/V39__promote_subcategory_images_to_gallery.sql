-- Promote legacy subcategory cover images into first-class gallery records.
-- Keep subcategories.image populated for backward compatibility with older clients.
INSERT INTO gallery_images (
    title,
    image_url,
    thumbnail_url,
    alt_text,
    display_order,
    is_featured,
    is_hero,
    subcategory_id,
    created_at,
    updated_at
)
SELECT
    s.name,
    TRIM(s.image),
    TRIM(s.image),
    s.name,
    COALESCE((
        SELECT MAX(existing.display_order) + 1
        FROM gallery_images existing
        WHERE existing.subcategory_id = s.id
    ), 0),
    FALSE,
    FALSE,
    s.id,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM subcategories s
WHERE s.image IS NOT NULL
  AND TRIM(s.image) <> ''
  AND NOT EXISTS (
      SELECT 1
      FROM gallery_images existing
      WHERE existing.subcategory_id = s.id
        AND TRIM(existing.image_url) = TRIM(s.image)
  );
