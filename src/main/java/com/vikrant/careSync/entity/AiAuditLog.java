package com.vikrant.careSync.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String username;

    @Column(nullable = false)
    private String promptType; // CHAT, SUMMARIZE, SUGGEST_DIAGNOSIS, STREAM

    @Column(nullable = false)
    private String selectedModel; // llama-3.3-70b-versatile, etc.

    @Column(nullable = false)
    private Long latencyMs;

    @Column(nullable = false)
    private boolean success;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private String bookingActionProduced; // SHOW_DOCTORS, SHOW_SLOTS, CONFIRM_BOOKING, NONE

    @Column(columnDefinition = "TEXT")
    private String userPrompt;

    @Column(columnDefinition = "TEXT")
    private String aiResponse;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
