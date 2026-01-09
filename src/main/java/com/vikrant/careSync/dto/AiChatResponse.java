package com.vikrant.careSync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiChatResponse {
    private String response;
    private boolean success;
    private String error;
    private AiBookingSuggestion suggestion;
}
