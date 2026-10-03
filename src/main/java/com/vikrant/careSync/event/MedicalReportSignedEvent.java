package com.vikrant.careSync.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class MedicalReportSignedEvent extends ApplicationEvent {

    private final Long appointmentId;
    private final Long medicalHistoryId;
    private final Long doctorId;
    private final Long patientId;

    public MedicalReportSignedEvent(Object source, Long appointmentId, Long medicalHistoryId, Long doctorId,
            Long patientId) {
        super(source);
        this.appointmentId = appointmentId;
        this.medicalHistoryId = medicalHistoryId;
        this.doctorId = doctorId;
        this.patientId = patientId;
    }
}
