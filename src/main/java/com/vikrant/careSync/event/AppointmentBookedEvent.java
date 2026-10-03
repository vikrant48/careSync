package com.vikrant.careSync.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AppointmentBookedEvent extends ApplicationEvent {

    private final Long appointmentId;
    private final Long doctorId;
    private final Long patientId;
    private final boolean emergency;

    public AppointmentBookedEvent(Object source, Long appointmentId, Long doctorId, Long patientId, boolean emergency) {
        super(source);
        this.appointmentId = appointmentId;
        this.doctorId = doctorId;
        this.patientId = patientId;
        this.emergency = emergency;
    }
}
