package com.vikrant.careSync.entity;

import com.vikrant.careSync.security.crypto.EncryptedTextConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One AI assistant chat of a user. publicId is the conversation id the browser and the chat memory use; the title
 * (taken from the first message) is encrypted because it can contain health details. Table created by Flyway V12.
 */
@Entity
@Table(name = "ai_conversations")
@Getter
@Setter
@NoArgsConstructor
public class AiConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 64)
    private String publicId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Convert(converter = EncryptedTextConverter.class)
    @Column(columnDefinition = "TEXT")
    private String title;

    @Column(name = "message_count", nullable = false)
    private int messageCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
