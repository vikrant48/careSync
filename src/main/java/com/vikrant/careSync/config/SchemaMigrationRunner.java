package com.vikrant.careSync.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SchemaMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public SchemaMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("Running Schema Fix for Appointment Status...");
        try {
            // Drop the old constraint
            jdbcTemplate.execute("ALTER TABLE appointments DROP CONSTRAINT IF EXISTS appointments_status_check");

            // Add the new constraint with updated enum values
            String sql = "ALTER TABLE appointments ADD CONSTRAINT appointments_status_check " +
                    "CHECK (status IN ('BOOKED', 'SCHEDULED', 'CONFIRMED', 'IN_PROGRESS', " +
                    "'COMPLETED', 'CANCELLED', 'CANCELLED_BY_PATIENT', 'CANCELLED_BY_DOCTOR'))";
            jdbcTemplate.execute(sql);

            System.out.println("Schema Fix Completed: Appointment status constraint updated.");
        } catch (Exception e) {
            System.err.println("Schema Fix Failed: " + e.getMessage());
            // Don't throw exception to allow app to start even if this fails (e.g. if
            // constraint doesn't exist)
        }
    }
}
