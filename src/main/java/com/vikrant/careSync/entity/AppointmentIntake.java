package com.vikrant.careSync.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "appointment_intakes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentIntake {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    @JsonIgnore
    private Appointment appointment;

    @Column(name = "chief_complaint", length = 500)
    private String chiefComplaint;

    @Column(name = "symptoms", columnDefinition = "TEXT")
    private String symptoms;

    @Column(name = "symptom_duration", length = 100)
    private String symptomDuration;

    @Column(name = "symptom_severity", length = 100)
    private String symptomSeverity;

    @Column(name = "current_medications", columnDefinition = "TEXT")
    private String currentMedications;

    @Column(name = "allergies", columnDefinition = "TEXT")
    private String allergies;

    @Column(name = "consent_given", columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean consentGiven = false;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "intake_status", length = 50)
    @Builder.Default
    private String intakeStatus = "SUBMITTED";

    @Column(name = "is_confirmed_by_patient", columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean isConfirmedByPatient = false;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "confirmed_by", length = 100)
    private String confirmedBy;

    @Column(name = "has_red_flags", columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean hasRedFlags = false;

    @Column(name = "red_flags", columnDefinition = "TEXT")
    private String redFlags;

    @Column(name = "edit_history", columnDefinition = "TEXT")
    private String editHistory;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
