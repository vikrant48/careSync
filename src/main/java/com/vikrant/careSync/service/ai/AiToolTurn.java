package com.vikrant.careSync.service.ai;

import java.util.List;

/**
 * The model's answer in a tool-calling conversation: either text for the user, or tool calls to run (then the
 * results are sent back and the model is asked again).
 */
public record AiToolTurn(String content, List<AiToolCall> toolCalls, String model, AiUsage usage) {

    public boolean wantsTools() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
