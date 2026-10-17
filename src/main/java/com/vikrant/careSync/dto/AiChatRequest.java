package com.vikrant.careSync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiChatRequest {
    /** Longest chat message accepted: limits AI token use per request (the frontend enforces it too) */
    public static final int MAX_MESSAGE_LENGTH = 2000;

    @NotBlank(message = "Message cannot be empty")
    @Size(max = MAX_MESSAGE_LENGTH, message = "Message is too long (at most 2000 characters)")
    private String message;

    private String conversationId;

    /** Doctors only, optional: the patient picked with @patient. Checked on the server (own patients only). */
    @Positive(message = "patientId must be a positive number")
    private Long patientId;
}
