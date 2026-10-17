package com.vikrant.careSync.service.ai;

/**
 * Which Groq model a task needs. FAST (gpt-oss-20b) for simple rewriting and extraction, SMART (gpt-oss-120b) for
 * chat and clinical reasoning. Splitting the work also spreads the two models' separate free daily quotas.
 */
public enum AiModelTier {
    SMART,
    FAST
}
