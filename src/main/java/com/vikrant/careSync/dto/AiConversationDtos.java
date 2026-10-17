package com.vikrant.careSync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** Saved AI chats as the chat widget shows them. */
public final class AiConversationDtos {

    private AiConversationDtos() {
    }

    /** One row of "Recent chats" */
    public record Summary(String id, String title, LocalDateTime updatedAt, int messageCount) {
    }

    /** One message: role "user" or "assistant", with the assistant's card and emergency notice */
    public record Message(String role, String text, AiBookingSuggestion suggestion, AiEmergencyInfo emergency,
            LocalDateTime createdAt) {
    }

    public record Conversation(String id, String title, LocalDateTime updatedAt, List<Message> messages) {
    }

    public record RenameRequest(@NotBlank @Size(max = 80) String title) {
    }
}
