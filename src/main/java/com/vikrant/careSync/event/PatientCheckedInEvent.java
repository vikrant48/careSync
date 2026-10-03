package com.vikrant.careSync.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PatientCheckedInEvent extends ApplicationEvent {

    private final Long appointmentId;
    private final Long doctorId;
    private final Long patientId;

    public PatientCheckedInEvent(Object source, Long appointmentId, Long doctorId, Long patientId) {
        super(source);
        this.appointmentId = appointmentId;
        this.doctorId = doctorId;
        this.patientId = patientId;
    }
}
