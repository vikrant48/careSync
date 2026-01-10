package com.vikrant.careSync.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "vitals")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vital {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    private Double systolicBP;
    private Double diastolicBP;
    private Double sugarLevel;
    private Double weight;
    private Double temperature;
    private Double heartRate;
    private Double respiratoryRate;

    @Column(nullable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    protected void onCreate() {
        if (recordedAt == null) {
            recordedAt = LocalDateTime.now();
        }
    }
}
