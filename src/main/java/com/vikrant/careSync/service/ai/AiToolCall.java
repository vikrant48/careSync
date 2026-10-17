package com.vikrant.careSync.service.ai;

/** One function call requested by the model: id (echoed back with the result), tool name and JSON arguments. */
public record AiToolCall(String id, String name, String argumentsJson) {
}
