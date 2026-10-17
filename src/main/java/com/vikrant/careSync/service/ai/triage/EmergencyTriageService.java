package com.vikrant.careSync.service.ai.triage;

import com.vikrant.careSync.dto.AiEmergencyInfo;
import com.vikrant.careSync.service.ai.AiAuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds the emergency / urgent-care notice and records every triage decision in the AI audit log (decision and
 * source only, never the patient's text). Numbers are settings (India by default: 112, ambulance 108,
 * Tele-MANAS 14416 for mental-health crises).
 */
@Service
@Slf4j
public class EmergencyTriageService {

    private final AiAuditService aiAuditService;
    private final String emergencyNumber;
    private final String ambulanceNumber;
    private final String mentalHealthNumber;
    private final String mentalHealthName;

    public EmergencyTriageService(AiAuditService aiAuditService,
            @Value("${app.emergency.number:112}") String emergencyNumber,
            @Value("${app.emergency.ambulance-number:108}") String ambulanceNumber,
            @Value("${app.emergency.mental-health-number:14416}") String mentalHealthNumber,
            @Value("${app.emergency.mental-health-name:Tele-MANAS}") String mentalHealthName) {
        this.aiAuditService = aiAuditService;
        this.emergencyNumber = emergencyNumber;
        this.ambulanceNumber = ambulanceNumber;
        this.mentalHealthNumber = mentalHealthNumber;
        this.mentalHealthName = mentalHealthName;
    }

    /** Layer 1: the deterministic red flags (no AI). Empty when nothing matched. */
    public Optional<AiEmergencyInfo> fromRules(String text) {
        return RedFlagRules.find(text).map(match -> {
            audit(TriageLevel.EMERGENCY, match.category(), "rules");
            return notice(TriageLevel.EMERGENCY, match.category(), List.of(match.phrase()));
        });
    }

    /** Layer 2: the AI flagged urgency through its triage tool. ROUTINE gives no notice. */
    public Optional<AiEmergencyInfo> fromAi(TriageLevel level, TriageCategory category, List<String> reasons) {
        if (level == null || level == TriageLevel.ROUTINE) {
            return Optional.empty();
        }
        TriageCategory safeCategory = category != null ? category : TriageCategory.MEDICAL;
        audit(level, safeCategory, "ai");
        return Optional.of(notice(level, safeCategory, reasons));
    }

    public AiEmergencyInfo notice(TriageLevel level, TriageCategory category, List<String> reasons) {
        List<String> cleanReasons = reasons == null ? List.of()
                : reasons.stream().filter(r -> r != null && !r.isBlank())
                        .map(r -> r.length() > 80 ? r.substring(0, 80) : r).limit(3).toList();
        List<AiEmergencyInfo.Contact> contacts = new ArrayList<>();

        if (category == TriageCategory.MENTAL_HEALTH) {
            contacts.add(new AiEmergencyInfo.Contact(mentalHealthName + " (free, 24x7)", mentalHealthNumber));
            contacts.add(new AiEmergencyInfo.Contact("Emergency", emergencyNumber));
            return AiEmergencyInfo.builder()
                    .level(TriageLevel.EMERGENCY.name()) // a crisis is always treated as an emergency
                    .category(category.name())
                    .title("You don't have to go through this alone")
                    .message("If you might hurt yourself or are in danger, call " + emergencyNumber + " now. "
                            + "You can talk to a trained counsellor at " + mentalHealthName + " (" + mentalHealthNumber
                            + "), free and any time. If you can, stay with someone you trust.")
                    .reasons(cleanReasons)
                    .contacts(contacts)
                    .emergencyBooking(false)
                    .build();
        }

        contacts.add(new AiEmergencyInfo.Contact("Emergency", emergencyNumber));
        contacts.add(new AiEmergencyInfo.Contact("Ambulance", ambulanceNumber));
        if (level == TriageLevel.EMERGENCY) {
            return AiEmergencyInfo.builder()
                    .level(level.name())
                    .category(category.name())
                    .title("This could be a medical emergency")
                    .message("Call " + emergencyNumber + " or an ambulance (" + ambulanceNumber + "), or go to the "
                            + "nearest emergency room now. Don't wait for an online appointment.")
                    .reasons(cleanReasons)
                    .contacts(contacts)
                    .emergencyBooking(true)
                    .build();
        }
        return AiEmergencyInfo.builder()
                .level(TriageLevel.URGENT.name())
                .category(category.name())
                .title("Please see a doctor today")
                .message("These symptoms should be checked by a doctor today. If they get worse, call "
                        + emergencyNumber + ".")
                .reasons(cleanReasons)
                .contacts(contacts)
                .emergencyBooking(true)
                .build();
    }

    private void audit(TriageLevel level, TriageCategory category, String source) {
        try {
            aiAuditService.logInteraction("TRIAGE", source, 0, true, null, null, null,
                    level.name() + "_" + category.name() + "_BY_" + source.toUpperCase(), null, null, null);
        } catch (Exception e) {
            log.warn("Could not record triage decision: {}", e.getClass().getSimpleName());
        }
    }
}
