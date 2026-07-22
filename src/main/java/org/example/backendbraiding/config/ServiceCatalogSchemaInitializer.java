package org.example.backendbraiding.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Repairs catalog columns when a restored database and Flyway history disagree.
 * Every statement is idempotent; Flyway remains the canonical migration path.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ServiceCatalogSchemaInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ServiceCatalogSchemaInitializer.class);
    private final JdbcTemplate jdbcTemplate;

    public ServiceCatalogSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Verifying service catalog database columns");
        jdbcTemplate.execute("ALTER TABLE service_items ADD COLUMN IF NOT EXISTS active BOOLEAN");
        jdbcTemplate.execute("UPDATE service_items SET active = TRUE WHERE active IS NULL");
        jdbcTemplate.execute("ALTER TABLE service_items ALTER COLUMN active SET DEFAULT TRUE");
        jdbcTemplate.execute("ALTER TABLE service_items ALTER COLUMN active SET NOT NULL");

        jdbcTemplate.execute("ALTER TABLE service_items ADD COLUMN IF NOT EXISTS display_order INTEGER");
        jdbcTemplate.execute("UPDATE service_items SET display_order = 0 WHERE display_order IS NULL");
        jdbcTemplate.execute("ALTER TABLE service_items ALTER COLUMN display_order SET DEFAULT 0");
        jdbcTemplate.execute("ALTER TABLE service_items ALTER COLUMN display_order SET NOT NULL");

        jdbcTemplate.execute("ALTER TABLE appointments ADD COLUMN IF NOT EXISTS selected_texture VARCHAR(100)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_service_item_active_order ON service_items(active, display_order, id)");
        log.info("Service catalog database columns verified");
    }
}
