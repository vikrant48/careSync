package com.vikrant.careSync.security.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
public class UserSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String sessionId;

    @Column(nullable = false)
    private String ipAddress;

    @Column(nullable = false)
    private Instant loginTime;

    @Column
    private Instant lastActivity;

    @Column(nullable = false)
    private boolean active = true;

    @Column
    private String userAgent;

    @Column(nullable = false)
    private String userType; // DOCTOR or PATIENT
} 