package com.vikrant.careSync.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String recipientType; // e.g., DOCTOR, PATIENT, ADMIN

    @Column(nullable = false)
    private Long recipientId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(name = "`type`", nullable = false)
    private String type; // e.g., appointment, system

    @Column(name = "`read`", nullable = false)
    private Boolean read = false;

    @Column(name = "`timestamp`", nullable = false)
    private LocalDateTime timestamp;

    @Column
    private String link; // optional client route
}