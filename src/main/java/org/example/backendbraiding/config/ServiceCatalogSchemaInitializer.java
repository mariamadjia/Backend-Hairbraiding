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
        removeLegacyAppointmentUniqueness();
        jdbcTemplate.execute("ALTER TABLE service_items ADD COLUMN IF NOT EXISTS active BOOLEAN");
        jdbcTemplate.execute("UPDATE service_items SET active = TRUE WHERE active IS NULL");
        jdbcTemplate.execute("ALTER TABLE service_items ALTER COLUMN active SET DEFAULT TRUE");
        jdbcTemplate.execute("ALTER TABLE service_items ALTER COLUMN active SET NOT NULL");

        jdbcTemplate.execute("ALTER TABLE service_items ADD COLUMN IF NOT EXISTS display_order INTEGER");
        jdbcTemplate.execute("UPDATE service_items SET display_order = 0 WHERE display_order IS NULL");
        jdbcTemplate.execute("ALTER TABLE service_items ALTER COLUMN display_order SET DEFAULT 0");
        jdbcTemplate.execute("ALTER TABLE service_items ALTER COLUMN display_order SET NOT NULL");

        jdbcTemplate.execute("ALTER TABLE appointments ADD COLUMN IF NOT EXISTS selected_texture VARCHAR(100)");
        jdbcTemplate.execute("ALTER TABLE service_items ADD COLUMN IF NOT EXISTS foundation_choices_enabled BOOLEAN");
        jdbcTemplate.execute("UPDATE service_items SET foundation_choices_enabled = FALSE WHERE foundation_choices_enabled IS NULL");
        jdbcTemplate.execute("ALTER TABLE service_items ALTER COLUMN foundation_choices_enabled SET DEFAULT FALSE");
        jdbcTemplate.execute("ALTER TABLE service_items ALTER COLUMN foundation_choices_enabled SET NOT NULL");

        jdbcTemplate.execute("ALTER TABLE service_items ADD COLUMN IF NOT EXISTS knotless_price_adjustment VARCHAR(255)");
        jdbcTemplate.execute("UPDATE service_items SET knotless_price_adjustment = '0' WHERE knotless_price_adjustment IS NULL");
        jdbcTemplate.execute("ALTER TABLE service_items ALTER COLUMN knotless_price_adjustment SET DEFAULT '0'");
        jdbcTemplate.execute("ALTER TABLE service_items ALTER COLUMN knotless_price_adjustment SET NOT NULL");

        jdbcTemplate.execute("ALTER TABLE appointments ADD COLUMN IF NOT EXISTS selected_foundation VARCHAR(20)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_service_item_active_order ON service_items(active, display_order, id)");
        log.info("Service catalog database columns verified");
    }

    private void removeLegacyAppointmentUniqueness() {
        jdbcTemplate.execute("""
            DO $$
            DECLARE
                record_to_drop RECORD;
            BEGIN
                FOR record_to_drop IN
                    SELECT constraint_record.conname AS constraint_name
                    FROM pg_constraint constraint_record
                    JOIN pg_class table_record ON table_record.oid = constraint_record.conrelid
                    JOIN pg_namespace schema_record ON schema_record.oid = table_record.relnamespace
                    WHERE schema_record.nspname = current_schema()
                      AND table_record.relname = 'appointments'
                      AND constraint_record.contype = 'u'
                      AND (
                          SELECT array_agg(attribute_record.attname ORDER BY key_record.ordinality)
                          FROM unnest(constraint_record.conkey)
                               WITH ORDINALITY AS key_record(attnum, ordinality)
                          JOIN pg_attribute attribute_record
                            ON attribute_record.attrelid = table_record.oid
                           AND attribute_record.attnum = key_record.attnum
                      ) = ARRAY['customer_id', 'appointment_date_time']::name[]
                LOOP
                    EXECUTE format(
                        'ALTER TABLE appointments DROP CONSTRAINT %I',
                        record_to_drop.constraint_name
                    );
                END LOOP;
            END
            $$;
            """);

        jdbcTemplate.execute("""
            DO $$
            DECLARE
                record_to_drop RECORD;
            BEGIN
                FOR record_to_drop IN
                    SELECT index_record.relname AS indexname
                    FROM pg_index index_metadata
                    JOIN pg_class table_record ON table_record.oid = index_metadata.indrelid
                    JOIN pg_namespace schema_record ON schema_record.oid = table_record.relnamespace
                    JOIN pg_class index_record ON index_record.oid = index_metadata.indexrelid
                    WHERE schema_record.nspname = current_schema()
                      AND table_record.relname = 'appointments'
                      AND index_metadata.indisunique
                      AND (
                          SELECT array_agg(attribute_record.attname ORDER BY key_record.ordinality)
                          FROM unnest(index_metadata.indkey)
                               WITH ORDINALITY AS key_record(attnum, ordinality)
                          JOIN pg_attribute attribute_record
                            ON attribute_record.attrelid = table_record.oid
                           AND attribute_record.attnum = key_record.attnum
                      ) = ARRAY['customer_id', 'appointment_date_time']::name[]
                LOOP
                    EXECUTE format('DROP INDEX IF EXISTS %I', record_to_drop.indexname);
                END LOOP;
            END
            $$;
            """);

        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_appointment_customer_datetime
            ON appointments (customer_id, appointment_date_time)
            """);
    }
}
