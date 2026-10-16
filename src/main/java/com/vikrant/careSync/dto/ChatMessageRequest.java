package com.vikrant.careSync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Inbound chat message. Sender role and id are taken from the logged-in session, so any
 * senderRole/senderId sent by the client is ignored.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessageRequest {
    private Long appointmentId;
    private String content;
}
