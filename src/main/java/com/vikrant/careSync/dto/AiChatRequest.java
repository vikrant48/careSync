package com.vikrant.careSync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiChatRequest {
    @NotBlank(message = "Message cannot be empty")
    private String message;

    private String conversationId;

    /** Doctors only, optional: the patient picked with @patient. Checked on the server (own patients only). */
    @Positive(message = "patientId must be a positive number")
    private Long patientId;
}
