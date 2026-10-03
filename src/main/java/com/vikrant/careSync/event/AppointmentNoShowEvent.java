package com.vikrant.careSync.event;

import com.vikrant.careSync.entity.Appointment;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AppointmentNoShowEvent extends ApplicationEvent {

    private final Long appointmentId;
    private final Long doctorId;
    private final Long patientId;
    private final Appointment.Status noShowStatus;

    public AppointmentNoShowEvent(Object source, Long appointmentId, Long doctorId, Long patientId,
            Appointment.Status noShowStatus) {
        super(source);
        this.appointmentId = appointmentId;
        this.doctorId = doctorId;
        this.patientId = patientId;
        this.noShowStatus = noShowStatus;
    }
}
