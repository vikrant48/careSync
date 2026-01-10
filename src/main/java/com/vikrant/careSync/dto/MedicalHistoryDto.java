package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.MedicalHistory;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalHistoryDto {
    private String message;
    private Long id;
    private LocalDate visitDate;
    private String symptoms;
    private String diagnosis;
    private String treatment;
    private String medicine;
    private String doses;
    private String notes;
    private Long doctorId;
    private String doctorName;
    private String doctorSpecialization;
    private Long appointmentId;
    private LocalDateTime createdDateTime;

    public MedicalHistoryDto(MedicalHistory medicalHistory) {
        this.message = "Medical history created successfully";
        this.id = medicalHistory.getId();
        this.visitDate = medicalHistory.getVisitDate();
        this.symptoms = medicalHistory.getSymptoms();
        this.diagnosis = medicalHistory.getDiagnosis();
        this.treatment = medicalHistory.getTreatment();
        this.medicine = medicalHistory.getMedicine();
        this.doses = medicalHistory.getDoses();
        this.notes = medicalHistory.getNotes();
        this.createdDateTime = LocalDateTime.now();
        this.appointmentId = medicalHistory.getAppointmentId();

        if (medicalHistory.getDoctor() != null) {
            this.doctorId = medicalHistory.getDoctor().getId();
            this.doctorName = medicalHistory.getDoctor().getFirstName() + " "
                    + medicalHistory.getDoctor().getLastName();
            this.doctorSpecialization = medicalHistory.getDoctor().getSpecialization();
        }
    }

    // Constructor with custom message
    public MedicalHistoryDto(MedicalHistory medicalHistory, String customMessage) {
        this(medicalHistory);
        this.message = customMessage;
    }
}
