package org.example.backendbraiding.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Safety net for deployments where Flyway was disabled or skipped while the
 * ordered gallery-image mapping was already active.
 *
 * The statements are idempotent. Keep the Flyway migrations as the canonical
 * schema history; this repair only prevents an incomplete deployment from
 * taking every category-backed gallery endpoint offline.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GallerySchemaRepair implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public GallerySchemaRepair(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                ALTER TABLE category_flipping_images
                ADD COLUMN IF NOT EXISTS display_order INTEGER
                """);

        jdbcTemplate.execute("""
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
                WHERE images.ctid = ordered_images.ctid
                """);

        jdbcTemplate.execute("""
                ALTER TABLE category_flipping_images
                ALTER COLUMN display_order SET NOT NULL
                """);

        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_category_flipping_images_order
                ON category_flipping_images (category_id, display_order)
                """);
    }
}
