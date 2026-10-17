package com.vikrant.careSync.service.ai.triage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** The shared emergency red flags: real emergencies caught, negations and look-alikes not. */
class RedFlagRulesTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "I have chest pain since an hour",
            "My father can't breathe properly",
            "she cannot breathe",
            "There is heavy bleeding from the cut",
            "the bleeding won't stop",
            "He passed out in the kitchen",
            "I think she is having a stroke",
            "his face is drooping and his speech is slurred",
            "my throat is swelling after eating peanuts",
            "my son swallowed bleach",
            "seene mein dard ho raha hai",
            "No fever. But I have chest pain now"
    })
    void medicalEmergencies(String text) {
        RedFlagRules.Match match = RedFlagRules.find(text).orElseThrow(() -> new AssertionError(text));
        assertEquals(TriageCategory.MEDICAL, match.category(), text);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "I want to kill myself",
            "I've been having suicidal thoughts",
            "sometimes I want to end my life",
            "I keep hurting myself",
            "mujhe marna chahta hu aisa lagta hai"
    })
    void mentalHealthCrises(String text) {
        RedFlagRules.Match match = RedFlagRules.find(text).orElseThrow(() -> new AssertionError(text));
        assertEquals(TriageCategory.MENTAL_HEALTH, match.category(), text);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "I have no chest pain, just a cough",
            "I don't have any trouble breathing",
            "no shortness of breath or fever",
            "I got heatstroke last summer, can I play cricket?",
            "is heat stroke common in May?",
            "Book a skin doctor tomorrow",
            "I have a mild headache",
            ""
    })
    void notEmergencies(String text) {
        assertTrue(RedFlagRules.find(text).isEmpty(), text);
    }

    @Test
    void negationOnlyCountsInTheSameClause() {
        assertTrue(RedFlagRules.isEmergency("No fever, but chest pain since morning"));
        assertFalse(RedFlagRules.isEmergency("Never had chest pain"));
    }

    @Test
    void nullIsNotAnEmergency() {
        assertFalse(RedFlagRules.isEmergency(null));
    }
}
