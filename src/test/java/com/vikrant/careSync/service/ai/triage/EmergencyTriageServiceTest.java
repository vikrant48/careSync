package com.vikrant.careSync.service.ai.triage;

import com.vikrant.careSync.dto.AiEmergencyInfo;
import com.vikrant.careSync.service.ai.AiAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Emergency notices: right numbers per case, every decision audited without the patient's text. */
class EmergencyTriageServiceTest {

    private AiAuditService audit;
    private EmergencyTriageService triage;

    @BeforeEach
    void setUp() {
        audit = mock(AiAuditService.class);
        triage = new EmergencyTriageService(audit, "112", "108", "14416", "Tele-MANAS");
    }

    @Test
    void medicalEmergencyFromRulesShowsEmergencyAndAmbulance() {
        AiEmergencyInfo notice = triage.fromRules("I have chest pain").orElseThrow();

        assertEquals("EMERGENCY", notice.getLevel());
        assertEquals("MEDICAL", notice.getCategory());
        assertEquals(List.of("112", "108"), notice.getContacts().stream().map(AiEmergencyInfo.Contact::getNumber).toList());
        assertTrue(notice.isEmergencyBooking());
        assertEquals(List.of("chest pain"), notice.getReasons());
        verify(audit).logInteraction(eq("TRIAGE"), eq("rules"), anyLong(), eq(true), isNull(), isNull(), isNull(),
                eq("EMERGENCY_MEDICAL_BY_RULES"), isNull(), isNull(), isNull());
    }

    @Test
    void mentalHealthCrisisPutsTheHelplineFirst() {
        AiEmergencyInfo notice = triage.fromRules("I want to kill myself").orElseThrow();

        assertEquals("MENTAL_HEALTH", notice.getCategory());
        assertEquals("14416", notice.getContacts().get(0).getNumber());
        assertTrue(notice.getMessage().contains("Tele-MANAS"));
        assertFalse(notice.isEmergencyBooking());
    }

    @Test
    void aiUrgentGivesSeeADoctorToday() {
        AiEmergencyInfo notice = triage.fromAi(TriageLevel.URGENT, TriageCategory.MEDICAL,
                List.of("high fever for 4 days")).orElseThrow();

        assertEquals("URGENT", notice.getLevel());
        assertTrue(notice.getTitle().contains("today"));
        verify(audit).logInteraction(eq("TRIAGE"), eq("ai"), anyLong(), eq(true), isNull(), isNull(), isNull(),
                eq("URGENT_MEDICAL_BY_AI"), isNull(), isNull(), isNull());
    }

    @Test
    void routineOrNothingGivesNoNotice() {
        assertTrue(triage.fromAi(TriageLevel.ROUTINE, TriageCategory.MEDICAL, List.of()).isEmpty());
        assertTrue(triage.fromRules("book a dentist").isEmpty());
        verifyNoInteractions(audit);
    }
}
