package com.vikrant.careSync.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** A doctor a patient saved (the heart on the doctor's profile). Table created by Flyway V11. */
@Entity
@Table(name = "patient_saved_doctors",
        uniqueConstraints = @UniqueConstraint(name = "uk_saved_doctor_patient_doctor", columnNames = { "patient_id", "doctor_id" }))
@Getter
@Setter
@NoArgsConstructor
public class PatientSavedDoctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public PatientSavedDoctor(Long patientId, Long doctorId) {
        this.patientId = patientId;
        this.doctorId = doctorId;
    }
}
