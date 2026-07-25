package org.example.backendbraiding.config;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

/**
 * Repairs pricing and appointment columns when a restored production database
 * and its Flyway history disagree.
 *
 * V18 remains the canonical migration. This startup safety net deliberately
 * executes the same idempotent SQL so a deployment where Flyway is disabled or
 * skipped cannot leave the pricing and appointment APIs unusable.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PricingSchemaRepair implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PricingSchemaRepair.class);
    private final DataSource dataSource;

    public PricingSchemaRepair(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Verifying pricing and appointment database columns");

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V18__repair_runtime_schema.sql"));
        populator.setContinueOnError(false);
        populator.execute(dataSource);

        log.info("Pricing and appointment database columns verified");
    }
}
