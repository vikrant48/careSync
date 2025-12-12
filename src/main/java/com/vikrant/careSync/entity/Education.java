package com.vikrant.careSync.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@Table(name = "educations")
public class Education {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String degree;
    private String institution;
    private int yearOfCompletion;
    private String details;

    @ManyToOne
    @JoinColumn(name = "doctor_id")
    @JsonBackReference
    private Doctor doctor;

}