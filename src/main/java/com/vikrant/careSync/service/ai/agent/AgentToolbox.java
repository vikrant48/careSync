package com.vikrant.careSync.service.ai.agent;

import java.util.List;
import java.util.Map;

/** A set of tools the agent may offer to the model: the patient tools or the doctor co-pilot tools. */
public interface AgentToolbox {

    /** Tool definitions in the OpenAI "tools" format */
    List<Map<String, Object>> definitions();

    /** Runs a tool by name; unknown tools and bad arguments become {"error": ...} for the model */
    AiToolResult execute(String name, String argumentsJson);
}
