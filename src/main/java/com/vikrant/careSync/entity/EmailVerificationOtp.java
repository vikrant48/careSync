package com.vikrant.careSync.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "email_verification_otps")
public class EmailVerificationOtp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(nullable = false, length = 6)
    private String otp;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    @Column(nullable = false)
    private Boolean verified = false;

    @Column(nullable = false)
    private Boolean used = false;

    @Column(length = 100)
    private String name;

    @Column(name = "mobile_number", length = 20)
    private String mobileNumber;

    @Column(name = "user_id")
    private Long userId;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (verified == null) verified = false;
        if (used == null) used = false;
    }
}