package com.vikrant.careSync.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "appointment_signatures")
public class AppointmentSignature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "appointment_id", nullable = false, unique = true)
    private Long appointmentId;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "signed_at", nullable = false)
    private LocalDateTime signedAt;

    @Column(name = "signed_by_name", nullable = false)
    private String signedByName;

    @Column(name = "doctor_reg_number")
    private String doctorRegNumber;

    @Column(name = "auth_method", length = 50)
    @Builder.Default
    private String authMethod = "PASSWORD_REENTRY";

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "content_hash")
    private String contentHash;

    @Column(name = "signature_image_url", columnDefinition = "TEXT")
    private String signatureImageUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (signedAt == null) {
            signedAt = LocalDateTime.now();
        }
    }
}
