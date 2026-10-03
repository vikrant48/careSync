package com.vikrant.careSync.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.vikrant.careSync.security.EncryptionConverter;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Setter
@Getter
@Entity
@Table(name = "medical_histories")
public class MedicalHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "patient_id")
    @JsonBackReference
    private Patient patient;

    @ManyToOne
    @JoinColumn(name = "doctor_id")
    private Doctor doctor;

    private LocalDate visitDate;

    @Convert(converter = EncryptionConverter.class)
    @Column(columnDefinition = "TEXT")
    private String symptoms;

    @Convert(converter = EncryptionConverter.class)
    @Column(columnDefinition = "TEXT")
    private String diagnosis;

    @Convert(converter = EncryptionConverter.class)
    @Column(columnDefinition = "TEXT")
    private String treatment;

    // Prescription fields
    @Convert(converter = EncryptionConverter.class)
    @Column(columnDefinition = "TEXT")
    private String medicine;

    @Convert(converter = EncryptionConverter.class)
    @Column(columnDefinition = "TEXT")
    private String doses;

    @Convert(converter = EncryptionConverter.class)
    @Column(columnDefinition = "TEXT")
    private String notes;

    // SOAP Format & AI Scribe Fields
    @Convert(converter = EncryptionConverter.class)
    @Column(columnDefinition = "TEXT")
    private String subjective;

    @Convert(converter = EncryptionConverter.class)
    @Column(columnDefinition = "TEXT")
    private String objective;

    @Convert(converter = EncryptionConverter.class)
    @Column(columnDefinition = "TEXT")
    private String assessment;

    @Convert(converter = EncryptionConverter.class)
    @Column(columnDefinition = "TEXT")
    private String plan;

    @Convert(converter = EncryptionConverter.class)
    @Column(columnDefinition = "TEXT")
    private String transcript;

    @Column(name = "is_draft", columnDefinition = "boolean default true")
    private Boolean isDraft = true;

    @Column(name = "is_signed", columnDefinition = "boolean default false")
    private Boolean isSigned = false;

    @Column(name = "signed_at")
    private java.time.LocalDateTime signedAt;

    @Column(name = "appointment_id")
    private Long appointmentId;

    // Getters and setters
    // ...
}