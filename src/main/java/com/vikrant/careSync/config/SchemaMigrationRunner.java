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
        System.out.println("Running Schema Fix for Appointment Status & Logs Constraints...");
        try {
            // Drop old constraints from appointments and appointment_status_logs
            jdbcTemplate.execute("ALTER TABLE appointments DROP CONSTRAINT IF EXISTS appointments_status_check");
            jdbcTemplate.execute(
                    "ALTER TABLE appointment_status_logs DROP CONSTRAINT IF EXISTS appointment_status_logs_new_status_check");
            jdbcTemplate.execute(
                    "ALTER TABLE appointment_status_logs DROP CONSTRAINT IF EXISTS appointment_status_logs_previous_status_check");

            // Add the comprehensive constraint for appointments table with all 7-stage
            // lifecycle statuses
            String sqlAppointments = "ALTER TABLE appointments ADD CONSTRAINT appointments_status_check " +
                    "CHECK (status IN ('BOOKED', 'CONFIRMED', 'SCHEDULED', 'INTAKE', 'INTAKE_COMPLETED', " +
                    "'WAITING_ROOM', 'READY_FOR_VISIT', 'IN_PROGRESS', 'MEDICAL_RECORD', 'REPORT_DRAFTED', 'READY_TO_COMPLETE', "
                    +
                    "'COMPLETED', 'CANCELLED', 'CANCELLED_BY_PATIENT', 'CANCELLED_BY_DOCTOR', 'REJECTED', 'NO_SHOW'))";
            jdbcTemplate.execute(sqlAppointments);

            // Dynamically drop all CHECK constraints on appointment_status_logs
            String dropLogsCheckConstraints = "DO $$ " +
                    "DECLARE r RECORD; " +
                    "BEGIN " +
                    "    FOR r IN (" +
                    "        SELECT constraint_name " +
                    "        FROM information_schema.table_constraints " +
                    "        WHERE table_name = 'appointment_status_logs' AND constraint_type = 'CHECK'" +
                    "    ) LOOP " +
                    "        EXECUTE 'ALTER TABLE appointment_status_logs DROP CONSTRAINT IF EXISTS ' || quote_ident(r.constraint_name); "
                    +
                    "    END LOOP; " +
                    "END $$;";
            jdbcTemplate.execute(dropLogsCheckConstraints);

            // Create appointment_signatures table if not exists
            String sqlSignaturesTable = "CREATE TABLE IF NOT EXISTS appointment_signatures (" +
                    "id BIGSERIAL PRIMARY KEY, " +
                    "appointment_id BIGINT NOT NULL UNIQUE REFERENCES appointments(id) ON DELETE CASCADE, " +
                    "doctor_id BIGINT NOT NULL, " +
                    "signed_at TIMESTAMP NOT NULL, " +
                    "signed_by_name VARCHAR(255) NOT NULL, " +
                    "doctor_reg_number VARCHAR(100), " +
                    "auth_method VARCHAR(50) DEFAULT 'PASSWORD_REENTRY', " +
                    "ip_address VARCHAR(100), " +
                    "content_hash VARCHAR(255), " +
                    "signature_image_url TEXT, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")";
            jdbcTemplate.execute(sqlSignaturesTable);

            // Create appointment_intakes table if not exists
            String sqlIntakesTable = "CREATE TABLE IF NOT EXISTS appointment_intakes (" +
                    "id BIGSERIAL PRIMARY KEY, " +
                    "appointment_id BIGINT NOT NULL UNIQUE REFERENCES appointments(id) ON DELETE CASCADE, " +
                    "chief_complaint VARCHAR(500), " +
                    "symptoms TEXT, " +
                    "symptom_duration VARCHAR(100), " +
                    "symptom_severity VARCHAR(100), " +
                    "current_medications TEXT, " +
                    "allergies TEXT, " +
                    "consent_given BOOLEAN DEFAULT FALSE, " +
                    "ai_summary TEXT, " +
                    "intake_status VARCHAR(50) DEFAULT 'SUBMITTED', " +
                    "is_confirmed_by_patient BOOLEAN DEFAULT FALSE, " +
                    "confirmed_at TIMESTAMP, " +
                    "confirmed_by VARCHAR(100), " +
                    "has_red_flags BOOLEAN DEFAULT FALSE, " +
                    "red_flags TEXT, " +
                    "edit_history TEXT, " +
                    "created_at TIMESTAMP, " +
                    "updated_at TIMESTAMP" +
                    ")";
            jdbcTemplate.execute(sqlIntakesTable);

            // Ensure columns exist if table was created previously
            jdbcTemplate.execute(
                    "ALTER TABLE appointment_intakes ADD COLUMN IF NOT EXISTS intake_status VARCHAR(50) DEFAULT 'SUBMITTED'");
            jdbcTemplate.execute(
                    "ALTER TABLE appointment_intakes ADD COLUMN IF NOT EXISTS is_confirmed_by_patient BOOLEAN DEFAULT FALSE");
            jdbcTemplate.execute("ALTER TABLE appointment_intakes ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMP");
            jdbcTemplate.execute("ALTER TABLE appointment_intakes ADD COLUMN IF NOT EXISTS confirmed_by VARCHAR(100)");
            jdbcTemplate.execute(
                    "ALTER TABLE appointment_intakes ADD COLUMN IF NOT EXISTS has_red_flags BOOLEAN DEFAULT FALSE");
            jdbcTemplate.execute("ALTER TABLE appointment_intakes ADD COLUMN IF NOT EXISTS red_flags TEXT");
            jdbcTemplate.execute("ALTER TABLE appointment_intakes ADD COLUMN IF NOT EXISTS edit_history TEXT");

            // Drop legacy intake columns from appointments table if they still exist
            jdbcTemplate.execute("ALTER TABLE appointments DROP COLUMN IF EXISTS chief_complaint");
            jdbcTemplate.execute("ALTER TABLE appointments DROP COLUMN IF EXISTS pre_visit_symptoms");
            jdbcTemplate.execute("ALTER TABLE appointments DROP COLUMN IF EXISTS pre_visit_summary");
            jdbcTemplate.execute("ALTER TABLE appointments DROP COLUMN IF EXISTS symptom_duration");
            jdbcTemplate.execute("ALTER TABLE appointments DROP COLUMN IF EXISTS symptom_severity");
            jdbcTemplate.execute("ALTER TABLE appointments DROP COLUMN IF EXISTS current_medications");
            jdbcTemplate.execute("ALTER TABLE appointments DROP COLUMN IF EXISTS allergies");
            jdbcTemplate.execute("ALTER TABLE appointments DROP COLUMN IF EXISTS consent_given");

            // Ensure medical_histories table has all SOAP & draft fields
            jdbcTemplate.execute("ALTER TABLE medical_histories ADD COLUMN IF NOT EXISTS subjective TEXT");
            jdbcTemplate.execute("ALTER TABLE medical_histories ADD COLUMN IF NOT EXISTS objective TEXT");
            jdbcTemplate.execute("ALTER TABLE medical_histories ADD COLUMN IF NOT EXISTS assessment TEXT");
            jdbcTemplate.execute("ALTER TABLE medical_histories ADD COLUMN IF NOT EXISTS plan TEXT");
            jdbcTemplate.execute("ALTER TABLE medical_histories ADD COLUMN IF NOT EXISTS transcript TEXT");
            jdbcTemplate
                    .execute("ALTER TABLE medical_histories ADD COLUMN IF NOT EXISTS is_draft BOOLEAN DEFAULT TRUE");
            jdbcTemplate
                    .execute("ALTER TABLE medical_histories ADD COLUMN IF NOT EXISTS is_signed BOOLEAN DEFAULT FALSE");
            jdbcTemplate.execute("ALTER TABLE medical_histories ADD COLUMN IF NOT EXISTS signed_at TIMESTAMP");
            jdbcTemplate.execute("ALTER TABLE medical_histories ADD COLUMN IF NOT EXISTS appointment_id BIGINT");

            System.out.println(
                    "Schema Fix Completed: Appointment status constraints, appointment_intakes table, medical_histories schema verified, and legacy columns dropped.");
        } catch (Exception e) {
            System.err.println("Schema Fix Warning: " + e.getMessage());
        }
    }
}
