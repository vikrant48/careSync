package com.vikrant.careSync.entity;

import com.vikrant.careSync.security.crypto.EncryptedTextConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * One message of an AI chat as the user saw it: the text, and for assistant replies the card and emergency notice
 * (JSON). All stored encrypted. Deleted with its conversation. Table created by Flyway V12.
 */
@Entity
@Table(name = "ai_messages")
@Getter
@Setter
@NoArgsConstructor
public class AiMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AiConversation conversation;

    /** "user" or "assistant" */
    @Column(nullable = false, length = 16)
    private String role;

    @Convert(converter = EncryptedTextConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Convert(converter = EncryptedTextConverter.class)
    @Column(name = "card_json", columnDefinition = "TEXT")
    private String cardJson;

    @Convert(converter = EncryptedTextConverter.class)
    @Column(name = "emergency_json", columnDefinition = "TEXT")
    private String emergencyJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
