package com.vikrant.careSync.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@Table(name = "experiences")
public class Experience {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String hospitalName;
    private String position;
    private int yearsOfService;
    private String details;

    @ManyToOne
    @JoinColumn(name = "doctor_id")
    @JsonBackReference
    private Doctor doctor;

}