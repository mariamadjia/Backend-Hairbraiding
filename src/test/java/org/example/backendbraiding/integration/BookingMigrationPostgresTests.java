package org.example.backendbraiding.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class BookingMigrationPostgresTests {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void forwardMigrationAddsBookingRangeColumnsWithoutRewritingRows() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            connection.createStatement().execute("""
                    CREATE TABLE appointments (
                        id bigserial PRIMARY KEY,
                        appointment_date_time timestamp NOT NULL
                    )
                    """);
            connection.createStatement().execute(
                    "INSERT INTO appointments (appointment_date_time) VALUES ('2026-08-01 10:00:00')");
        }

        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .baselineOnMigrate(true).baselineVersion("0").load().migrate();

        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             ResultSet columns = connection.getMetaData().getColumns(null, null, "appointments", null)) {
            boolean endTime = false;
            boolean duration = false;
            while (columns.next()) {
                endTime |= "appointment_end_date_time".equals(columns.getString("COLUMN_NAME"));
                duration |= "duration_minutes".equals(columns.getString("COLUMN_NAME"));
            }
            assertTrue(endTime && duration);
            ResultSet existing = connection.createStatement().executeQuery(
                    "SELECT appointment_end_date_time, duration_minutes FROM appointments LIMIT 1");
            assertTrue(existing.next());
            assertTrue(existing.getObject(1) == null && existing.getObject(2) == null,
                    "Migration must not backfill historical appointments");
        }
    }

    @Test
    void settingsRowLockSerializesCompetingCapacityChecks() throws Exception {
        try (Connection setup = connection()) {
            setup.createStatement().execute("CREATE TABLE booking_lock_settings (id bigint PRIMARY KEY)");
            setup.createStatement().execute("INSERT INTO booking_lock_settings VALUES (1)");
            setup.createStatement().execute("CREATE TABLE booking_lock_appointments (id bigserial PRIMARY KEY)");
        }

        try (Connection first = connection()) {
            first.setAutoCommit(false);
            first.createStatement().executeQuery("SELECT id FROM booking_lock_settings WHERE id=1 FOR UPDATE");

            var executor = Executors.newSingleThreadExecutor();
            CountDownLatch secondStarted = new CountDownLatch(1);
            var secondResult = executor.submit(() -> {
                try (Connection second = connection()) {
                    second.setAutoCommit(false);
                    secondStarted.countDown();
                    second.createStatement().executeQuery("SELECT id FROM booking_lock_settings WHERE id=1 FOR UPDATE");
                    ResultSet count = second.createStatement().executeQuery("SELECT COUNT(*) FROM booking_lock_appointments");
                    count.next();
                    long observed = count.getLong(1);
                    if (observed == 0) second.createStatement().execute("INSERT INTO booking_lock_appointments DEFAULT VALUES");
                    second.commit();
                    return observed;
                }
            });

            assertTrue(secondStarted.await(2, TimeUnit.SECONDS));
            first.createStatement().execute("INSERT INTO booking_lock_appointments DEFAULT VALUES");
            first.commit();
            assertEquals(1L, secondResult.get(5, TimeUnit.SECONDS));
            executor.shutdownNow();
        }

        try (Connection verify = connection()) {
            ResultSet count = verify.createStatement().executeQuery("SELECT COUNT(*) FROM booking_lock_appointments");
            count.next();
            assertEquals(1L, count.getLong(1));
        }
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
