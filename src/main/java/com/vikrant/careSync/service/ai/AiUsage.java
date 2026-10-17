package com.vikrant.careSync.service.ai;

/** Tokens one AI call used, as reported by the provider (any value may be null when not reported). */
public record AiUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {

    public static final AiUsage NONE = new AiUsage(null, null, null);
}
