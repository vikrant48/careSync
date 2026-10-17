package com.vikrant.careSync.service.ai.triage;

/** How quickly the patient needs care. EMERGENCY: now (call emergency services). URGENT: today. */
public enum TriageLevel {
    EMERGENCY,
    URGENT,
    ROUTINE
}
