package com.vikrant.careSync.service;

import com.vikrant.careSync.entity.MedicalHistory;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.dto.MedicalHistoryWithDoctorDto;
import com.vikrant.careSync.repository.MedicalHistoryRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.service.interfaces.IMedicalHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MedicalHistoryService implements IMedicalHistoryService {

    private final MedicalHistoryRepository medicalHistoryRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;

    @Override
    public MedicalHistory createMedicalHistory(MedicalHistory medicalHistory) {
        // Validate medical history
        if (medicalHistory.getPatient() == null || medicalHistory.getPatient().getId() == null) {
            throw new RuntimeException("Patient is required");
        }
        if (medicalHistory.getVisitDate() == null) {
            throw new RuntimeException("Visit date is required");
        }
        if (medicalHistory.getSymptoms() == null || medicalHistory.getSymptoms().trim().isEmpty()) {
            throw new RuntimeException("Symptoms are required");
        }
        if (medicalHistory.getDiagnosis() == null || medicalHistory.getDiagnosis().trim().isEmpty()) {
            throw new RuntimeException("Diagnosis is required");
        }

        // Validate patient exists
        Patient patient = patientRepository.findById(medicalHistory.getPatient().getId())
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        medicalHistory.setPatient(patient);
        return medicalHistoryRepository.save(medicalHistory);
    }

    public MedicalHistory createMedicalHistoryWithDoctor(MedicalHistory medicalHistory, Long doctorId) {
        // Validate medical history
        if (medicalHistory.getPatient() == null || medicalHistory.getPatient().getId() == null) {
            throw new RuntimeException("Patient is required");
        }
        if (doctorId == null) {
            throw new RuntimeException("Doctor is required");
        }
        if (medicalHistory.getVisitDate() == null) {
            throw new RuntimeException("Visit date is required");
        }
        if (medicalHistory.getSymptoms() == null || medicalHistory.getSymptoms().trim().isEmpty()) {
            throw new RuntimeException("Symptoms are required");
        }
        if (medicalHistory.getDiagnosis() == null || medicalHistory.getDiagnosis().trim().isEmpty()) {
            throw new RuntimeException("Diagnosis is required");
        }

        // Validate patient and doctor exist
        Patient patient = patientRepository.findById(medicalHistory.getPatient().getId())
                .orElseThrow(() -> new RuntimeException("Patient not found"));
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        medicalHistory.setPatient(patient);
        medicalHistory.setDoctor(doctor);
        return medicalHistoryRepository.save(medicalHistory);
    }

    @Override
    public MedicalHistory getMedicalHistoryById(Long id) {
        return medicalHistoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Medical history not found"));
    }

    @Override
    public List<MedicalHistory> getMedicalHistoryByPatient(Long patientId) {
        return medicalHistoryRepository.findByPatientId(patientId);
    }

    public List<MedicalHistory> getRecentMedicalHistory(Long patientId, int limit) {
        return medicalHistoryRepository.findByPatientIdOrderByVisitDateDesc(patientId)
                .stream()
                .limit(limit)
                .toList();
    }

    public List<MedicalHistory> getMedicalHistoryByDateRange(Long patientId, String startDate, String endDate) {
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            
            return medicalHistoryRepository.findByPatientId(patientId).stream()
                    .filter(history -> {
                        LocalDate visitDate = history.getVisitDate();
                        return !visitDate.isBefore(start) && !visitDate.isAfter(end);
                    })
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("Invalid date format. Use YYYY-MM-DD");
        }
    }

    @Override
    public List<MedicalHistory> getMedicalHistoryByDateRange(Long patientId, LocalDate startDate, LocalDate endDate) {
        return medicalHistoryRepository.findByPatientIdAndVisitDateBetween(patientId, startDate, endDate);
    }

    @Override
    public MedicalHistory updateMedicalHistory(Long id, MedicalHistory updatedHistory) {
        MedicalHistory existingHistory = medicalHistoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Medical history not found"));

        // Update fields if provided
        if (updatedHistory.getVisitDate() != null) {
            existingHistory.setVisitDate(updatedHistory.getVisitDate());
        }
        if (updatedHistory.getSymptoms() != null && !updatedHistory.getSymptoms().trim().isEmpty()) {
            existingHistory.setSymptoms(updatedHistory.getSymptoms());
        }
        if (updatedHistory.getDiagnosis() != null && !updatedHistory.getDiagnosis().trim().isEmpty()) {
            existingHistory.setDiagnosis(updatedHistory.getDiagnosis());
        }
        if (updatedHistory.getTreatment() != null) {
            existingHistory.setTreatment(updatedHistory.getTreatment());
        }
        if (updatedHistory.getMedicine() != null) {
            existingHistory.setMedicine(updatedHistory.getMedicine());
        }
        if (updatedHistory.getDoses() != null) {
            existingHistory.setDoses(updatedHistory.getDoses());
        }
        if (updatedHistory.getNotes() != null) {
            existingHistory.setNotes(updatedHistory.getNotes());
        }

        return medicalHistoryRepository.save(existingHistory);
    }

    @Override
    public void deleteMedicalHistory(Long id) {
        MedicalHistory medicalHistory = medicalHistoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Medical history not found"));
        medicalHistoryRepository.delete(medicalHistory);
    }

    @Override
    public Map<String, Object> getMedicalHistorySummary(Long patientId) {
        List<MedicalHistory> histories = medicalHistoryRepository.findByPatientId(patientId);
        
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalVisits", histories.size());
        summary.put("patientId", patientId);
        
        if (!histories.isEmpty()) {
            summary.put("firstVisit", histories.stream()
                    .mapToLong(h -> h.getVisitDate().toEpochDay())
                    .min()
                    .orElse(0));
            
            summary.put("lastVisit", histories.stream()
                    .mapToLong(h -> h.getVisitDate().toEpochDay())
                    .max()
                    .orElse(0));
            
            // Get unique diagnoses
            List<String> diagnoses = histories.stream()
                    .map(MedicalHistory::getDiagnosis)
                    .distinct()
                    .toList();
            summary.put("uniqueDiagnoses", diagnoses);
            summary.put("diagnosisCount", diagnoses.size());
        }
        
        return summary;
    }

    @Override
    public List<MedicalHistory> getMedicalHistoryByDiagnosis(Long patientId, String diagnosis) {
        return medicalHistoryRepository.findByPatientId(patientId).stream()
                .filter(history -> history.getDiagnosis().toLowerCase().contains(diagnosis.toLowerCase()))
                .toList();
    }

    @Override
    public List<MedicalHistoryWithDoctorDto> getMedicalHistoryWithDoctorByPatient(Long patientId) {
        // Get all completed appointments for the patient
        List<Appointment> completedAppointments = appointmentRepository.findByPatientIdAndStatus(patientId, Appointment.Status.COMPLETED);
        
        // Get all medical histories for the patient
        List<MedicalHistory> medicalHistories = medicalHistoryRepository.findByPatientId(patientId);
        
        List<MedicalHistoryWithDoctorDto> result = new ArrayList<>();
        
        // For each medical history, try to find a matching completed appointment
        for (MedicalHistory history : medicalHistories) {
            // Find the closest completed appointment by date
            Appointment matchingAppointment = completedAppointments.stream()
                    .filter(appointment -> appointment.getAppointmentDateTime().toLocalDate().equals(history.getVisitDate()) ||
                                         appointment.getAppointmentDateTime().toLocalDate().isEqual(history.getVisitDate()))
                    .findFirst()
                    .orElse(null);
            
            if (matchingAppointment != null) {
                MedicalHistoryWithDoctorDto dto = new MedicalHistoryWithDoctorDto(
                    history,
                    matchingAppointment.getDoctor(),
                    matchingAppointment.getId(),
                    matchingAppointment.getAppointmentDateTime().toString()
                );
                result.add(dto);
            }
        }
        
        return result;
    }
}