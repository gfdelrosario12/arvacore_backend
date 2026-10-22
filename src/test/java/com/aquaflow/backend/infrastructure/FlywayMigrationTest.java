package com.aquaflow.backend.infrastructure;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FlywayMigrationTest {

    @Test
    @DisplayName("Verify Flyway migrations validate and execute cleanly")
    public void shouldApplyMigrationsSuccessfully() {
        Flyway flyway = Flyway.configure()
                .dataSource("jdbc:h2:mem:flyway_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1", "sa", "")
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .load();

        int applied = flyway.migrate().migrationsExecuted;
        assertEquals(18, applied);
    }
}

