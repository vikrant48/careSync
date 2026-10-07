package com.vikrant.careSync.service;

import com.vikrant.careSync.dto.RecordReadinessDto;
import com.vikrant.careSync.entity.MedicalHistory;
import com.vikrant.careSync.repository.MedicalHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecordReadinessService {

    private final MedicalHistoryRepository medicalHistoryRepository;

    @Transactional(readOnly = true)
    public RecordReadinessDto checkReadiness(Long appointmentId) {
        List<MedicalHistory> records = medicalHistoryRepository.findAllByAppointmentId(appointmentId);

        List<String> missingFields = new ArrayList<>();
        List<String> reasons = new ArrayList<>();

        if (records.isEmpty()) {
            reasons.add("Save the AI SOAP first");
            reasons.add("Save the medical record first");
            missingFields.add("diagnosis");
            missingFields.add("soap_subjective");
            missingFields.add("soap_assessment");
            missingFields.add("soap_plan");

            return RecordReadinessDto.builder()
                    .appointmentId(appointmentId)
                    .isReady(false)
                    .recordSaved(false)
                    .soapSaved(false)
                    .missingFields(missingFields)
                    .reasons(reasons)
                    .build();
        }

        // 1. Check SOAP completeness across any record for this appointment
        boolean hasSubjective = records.stream()
                .anyMatch(r -> r.getSubjective() != null && !r.getSubjective().trim().isEmpty());
        boolean hasAssessment = records.stream()
                .anyMatch(r -> r.getAssessment() != null && !r.getAssessment().trim().isEmpty());
        boolean hasPlan = records.stream().anyMatch(r -> r.getPlan() != null && !r.getPlan().trim().isEmpty());
        boolean soapSaved = hasSubjective && hasAssessment && hasPlan;

        if (!soapSaved) {
            reasons.add("Save the AI SOAP first");
            if (!hasSubjective)
                missingFields.add("soap_subjective");
            if (!hasAssessment)
                missingFields.add("soap_assessment");
            if (!hasPlan)
                missingFields.add("soap_plan");
        }

        // 2. Check Medical Record completeness & mandatory fields across any record for
        // this appointment
        boolean hasDiagnosis = records.stream()
                .anyMatch(r -> r.getDiagnosis() != null && !r.getDiagnosis().trim().isEmpty());
        boolean recordSaved = hasDiagnosis;

        if (!hasDiagnosis) {
            reasons.add("Save the medical record first with mandatory field: diagnosis");
            missingFields.add("diagnosis");
        }

        boolean isReady = soapSaved && recordSaved;

        return RecordReadinessDto.builder()
                .appointmentId(appointmentId)
                .isReady(isReady)
                .recordSaved(recordSaved)
                .soapSaved(soapSaved)
                .missingFields(missingFields)
                .reasons(reasons)
                .build();
    }
}
