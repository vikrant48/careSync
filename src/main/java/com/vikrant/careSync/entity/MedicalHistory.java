package com.vikrant.careSync.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Setter
@Getter
@Entity
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
    private String symptoms;
    private String diagnosis;
    private String treatment;
    
    // Prescription fields
    private String medicine;
    private String doses;
    private String notes;

    // Getters and setters
    // ...
}