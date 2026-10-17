package com.vikrant.careSync.service.ai.agent;

import com.vikrant.careSync.dto.AiBookingSuggestion;

/**
 * What a tool returns: compact JSON for the model, and optionally a card for the patient (doctor list, slots,
 * confirm, appointments). The chat shows the last card produced in a turn.
 */
public record AiToolResult(String forModel, AiBookingSuggestion card) {

    public static AiToolResult data(String json) {
        return new AiToolResult(json, null);
    }
}
