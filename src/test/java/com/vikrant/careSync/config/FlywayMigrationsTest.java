package com.vikrant.careSync.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** The migration set is complete, ordered, and every script can run the way Flyway will run it. */
class FlywayMigrationsTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    @Test
    void flywayResolvesEveryScriptInOrder() {
        Flyway flyway = Flyway.configure()
                .dataSource("jdbc:h2:mem:flyway_check;MODE=PostgreSQL", "sa", "")
                .locations("classpath:db/migration")
                .load();
        MigrationInfo[] pending = flyway.info().pending();
        List<String> versions = Arrays.stream(pending).map(info -> info.getVersion().getVersion()).toList();
        assertEquals(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"), versions);
    }

    @Test
    void scriptsDoNotManageTransactionsThemselves() throws IOException {
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".sql")).toList()) {
                String sql = Files.readString(file);
                String statements = sql.lines().filter(l -> !l.startsWith("--")).reduce("", (a, b) -> a + b + "\n");
                assertFalse(statements.matches("(?is).*^\\s*(BEGIN|COMMIT)\\s*;.*"),
                        file + ": Flyway already wraps each script in a transaction");
                if (statements.contains("CONCURRENTLY")) {
                    assertTrue(Files.exists(Path.of(file + ".conf")), file + " needs executeInTransaction=false");
                    assertTrue(statements.lines().filter(l -> !l.isBlank())
                            .allMatch(l -> l.contains("CONCURRENTLY")),
                            file + ": non-transactional scripts must not mix in other statements");
                }
            }
        }
    }

    @Test
    void baselineCreatesTheTablesTheAppNeeds() throws IOException {
        String baseline = Files.readString(MIGRATIONS.resolve("V1__baseline_schema.sql"));
        for (String table : List.of("users", "patients", "doctors", "appointments", "medical_histories",
                "appointment_intakes", "payments", "patient_payment_preferences", "refresh_token", "user_session",
                "audit_logs", "documents", "chat_messages")) {
            assertTrue(baseline.contains("create table " + table + " ("), table);
        }
    }
}
