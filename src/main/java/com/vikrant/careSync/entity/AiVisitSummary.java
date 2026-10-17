package com.vikrant.careSync.entity;

import com.vikrant.careSync.security.crypto.EncryptedTextConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * The patient's plain-language summary of a completed visit, saved the first time they open it. sourceHash is a
 * hash of the visit record it was written from: when the doctor amends the record the summary is made again.
 * Stored encrypted; deleted with its appointment. Table created by Flyway V13.
 */
@Entity
@Table(name = "ai_visit_summaries")
@Getter
@Setter
@NoArgsConstructor
public class AiVisitSummary {

    @Id
    @Column(name = "appointment_id")
    private Long appointmentId;

    @Convert(converter = EncryptedTextConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "source_hash", nullable = false, length = 64)
    private String sourceHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
