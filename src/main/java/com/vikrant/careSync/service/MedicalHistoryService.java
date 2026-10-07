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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

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

        MedicalHistory target = null;
        if (medicalHistory.getId() != null) {
            target = medicalHistoryRepository.findById(medicalHistory.getId()).orElse(null);
        } else if (medicalHistory.getAppointmentId() != null) {
            List<MedicalHistory> existingList = medicalHistoryRepository
                    .findAllByAppointmentId(medicalHistory.getAppointmentId());
            if (!existingList.isEmpty()) {
                target = existingList.get(0);
            }
        }

        if (target == null) {
            target = medicalHistory;
        } else {
            if (medicalHistory.getVisitDate() != null)
                target.setVisitDate(medicalHistory.getVisitDate());
            if (medicalHistory.getSymptoms() != null)
                target.setSymptoms(medicalHistory.getSymptoms());
            if (medicalHistory.getDiagnosis() != null)
                target.setDiagnosis(medicalHistory.getDiagnosis());
            if (medicalHistory.getTreatment() != null)
                target.setTreatment(medicalHistory.getTreatment());
            if (medicalHistory.getMedicine() != null)
                target.setMedicine(medicalHistory.getMedicine());
            if (medicalHistory.getDoses() != null)
                target.setDoses(medicalHistory.getDoses());
            if (medicalHistory.getNotes() != null)
                target.setNotes(medicalHistory.getNotes());
            if (medicalHistory.getAppointmentId() != null)
                target.setAppointmentId(medicalHistory.getAppointmentId());
        }

        target.setPatient(patient);
        target.setDoctor(doctor);
        return medicalHistoryRepository.save(target);
    }

    @Override
    public MedicalHistory getMedicalHistoryById(Long id) {
        return medicalHistoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Medical history not found"));
    }

    @Override
    public List<MedicalHistory> getMedicalHistoryByPatient(Long patientId) {
        return medicalHistoryRepository.findByPatientId(patientId).stream()
                .filter(mh -> !Boolean.TRUE.equals(mh.getIsDraft()))
                .toList();
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

        if (Boolean.TRUE.equals(existingHistory.getIsSigned()) || Boolean.FALSE.equals(existingHistory.getIsDraft())) {
            throw new IllegalStateException(
                    "Medical record is electronically signed and locked. Modifications post-completion are prohibited.");
        }

        if (existingHistory.getAppointmentId() != null) {
            Appointment appt = appointmentRepository.findById(existingHistory.getAppointmentId()).orElse(null);
            if (appt != null && appt.getStatus() == Appointment.Status.COMPLETED) {
                throw new IllegalStateException("Appointment is completed. Medical record is locked.");
            }
        }

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
        if (updatedHistory.getAppointmentId() != null) {
            existingHistory.setAppointmentId(updatedHistory.getAppointmentId());
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
        // Get all appointments for the patient
        List<Appointment> allAppointments = appointmentRepository.findByPatientId(patientId);

        // Get all medical histories for the patient
        List<MedicalHistory> medicalHistories = medicalHistoryRepository.findByPatientId(patientId);

        List<MedicalHistoryWithDoctorDto> result = new ArrayList<>();

        for (MedicalHistory history : medicalHistories) {
            Appointment matchingAppointment = null;

            // 1. Try to find by explicit appointmentId
            if (history.getAppointmentId() != null) {
                matchingAppointment = allAppointments.stream()
                        .filter(a -> a.getId().equals(history.getAppointmentId()))
                        .findFirst()
                        .orElse(null);
            }

            // 2. Fallback to closest appointment by date (legacy support)
            if (matchingAppointment == null) {
                matchingAppointment = allAppointments.stream()
                        .filter(appointment -> appointment.getAppointmentDateTime().toLocalDate()
                                .equals(history.getVisitDate()))
                        .findFirst()
                        .orElse(null);
            }

            // SECURITY GUARD: Only expose record to patient if appointment status is
            // COMPLETED
            if (matchingAppointment != null && matchingAppointment.getStatus() == Appointment.Status.COMPLETED) {
                MedicalHistoryWithDoctorDto dto = new MedicalHistoryWithDoctorDto(
                        history,
                        matchingAppointment.getDoctor(),
                        matchingAppointment.getId(),
                        matchingAppointment.getAppointmentDateTime().toString());
                result.add(dto);
            }
        }

        return result;
    }

    @Override
    public MedicalHistory getMedicalHistoryByAppointmentId(Long appointmentId) {
        List<MedicalHistory> records = medicalHistoryRepository.findAllByAppointmentId(appointmentId);
        if (records.isEmpty()) {
            return null;
        }
        if (records.size() == 1) {
            return records.get(0);
        }
        MedicalHistory primary = records.get(0);
        for (int i = 1; i < records.size(); i++) {
            MedicalHistory secondary = records.get(i);
            if (primary.getSubjective() == null || primary.getSubjective().trim().isEmpty()) {
                primary.setSubjective(secondary.getSubjective());
            }
            if (primary.getObjective() == null || primary.getObjective().trim().isEmpty()) {
                primary.setObjective(secondary.getObjective());
            }
            if (primary.getAssessment() == null || primary.getAssessment().trim().isEmpty()) {
                primary.setAssessment(secondary.getAssessment());
            }
            if (primary.getPlan() == null || primary.getPlan().trim().isEmpty()) {
                primary.setPlan(secondary.getPlan());
            }
            if (primary.getDiagnosis() == null || primary.getDiagnosis().trim().isEmpty()) {
                primary.setDiagnosis(secondary.getDiagnosis());
            }
            if (primary.getTreatment() == null || primary.getTreatment().trim().isEmpty()) {
                primary.setTreatment(secondary.getTreatment());
            }
            if (primary.getMedicine() == null || primary.getMedicine().trim().isEmpty()) {
                primary.setMedicine(secondary.getMedicine());
            }
            if (primary.getDoses() == null || primary.getDoses().trim().isEmpty()) {
                primary.setDoses(secondary.getDoses());
            }
            if (primary.getNotes() == null || primary.getNotes().trim().isEmpty()) {
                primary.setNotes(secondary.getNotes());
            }
            if (primary.getSymptoms() == null || primary.getSymptoms().trim().isEmpty()) {
                primary.setSymptoms(secondary.getSymptoms());
            }
        }
        return medicalHistoryRepository.save(primary);
    }
}