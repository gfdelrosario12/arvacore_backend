package com.aquaflow.backend.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FlywayMigrationIntegrityTest {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^V(\\d+)__.*\\.sql$");

    @Test
    @DisplayName("Verify that all migration filenames follow strict forward-only versioning without duplicates")
    public void testMigrationNamingAndVersioningIntegrity() {
        File migrationDir = new File("src/main/resources/db/migration");
        assertTrue(migrationDir.exists() && migrationDir.isDirectory(), "Migration directory must exist");

        File[] files = migrationDir.listFiles((dir, name) -> name.endsWith(".sql"));
        assertTrue(files != null && files.length > 0, "Migration files must be present");

        Set<Integer> seenVersions = new HashSet<>();
        for (File file : files) {
            Matcher m = VERSION_PATTERN.matcher(file.getName());
            assertTrue(m.matches(), "Migration file " + file.getName() + " does not match pattern V<version>__<description>.sql");
            int version = Integer.parseInt(m.group(1));
            assertFalse(seenVersions.contains(version), "Duplicate Flyway migration version detected: " + version);
            seenVersions.add(version);
        }
    }
}
