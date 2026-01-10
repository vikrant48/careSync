package com.vikrant.careSync.service;

import com.vikrant.careSync.dto.VitalDto;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.Vital;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.repository.VitalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VitalService {

    private final VitalRepository vitalRepository;
    private final PatientRepository patientRepository;

    @Transactional
    public VitalDto logVital(VitalDto dto) {
        Patient patient = patientRepository.findById(dto.getPatientId())
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        Vital vital = Vital.builder()
                .patient(patient)
                .systolicBP(dto.getSystolicBP())
                .diastolicBP(dto.getDiastolicBP())
                .sugarLevel(dto.getSugarLevel())
                .weight(dto.getWeight())
                .temperature(dto.getTemperature())
                .heartRate(dto.getHeartRate())
                .respiratoryRate(dto.getRespiratoryRate())
                .recordedAt(dto.getRecordedAt())
                .build();

        Vital saved = vitalRepository.save(vital);
        return new VitalDto(saved);
    }

    public List<VitalDto> getPatientVitals(Long patientId) {
        return vitalRepository.findByPatientIdOrderByRecordedAtDesc(patientId).stream()
                .map(VitalDto::new)
                .collect(Collectors.toList());
    }
}
