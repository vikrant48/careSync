package com.vikrant.careSync.service.ai.agent;

import com.vikrant.careSync.dto.AiBookingSuggestion;
import com.vikrant.careSync.dto.AiEmergencyInfo;

/**
 * What a tool returns: compact JSON for the model, optionally a card for the patient (doctor list, slots, confirm,
 * appointments) and optionally an emergency / urgent-care notice. The chat shows the last card of a turn.
 */
public record AiToolResult(String forModel, AiBookingSuggestion card, AiEmergencyInfo emergency) {

    public AiToolResult(String forModel, AiBookingSuggestion card) {
        this(forModel, card, null);
    }

    public static AiToolResult data(String json) {
        return new AiToolResult(json, null);
    }
}
