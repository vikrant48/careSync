package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.MedicalHistory;
import com.vikrant.careSync.entity.Doctor;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MedicalHistoryWithDoctorDto {
    private Long id;
    private String visitDate;
    private String symptoms;
    private String diagnosis;
    private String treatment;
    private String doctorName;
    private String doctorSpecialization;
    private Long appointmentId;
    private String appointmentDate;
    
    public MedicalHistoryWithDoctorDto(MedicalHistory medicalHistory, Doctor doctor, Long appointmentId, String appointmentDate) {
        this.id = medicalHistory.getId();
        this.visitDate = medicalHistory.getVisitDate().toString();
        this.symptoms = medicalHistory.getSymptoms();
        this.diagnosis = medicalHistory.getDiagnosis();
        this.treatment = medicalHistory.getTreatment();
        this.doctorName = doctor.getFirstName() + " " + doctor.getLastName();
        this.doctorSpecialization = doctor.getSpecialization();
        this.appointmentId = appointmentId;
        this.appointmentDate = appointmentDate;
    }
}