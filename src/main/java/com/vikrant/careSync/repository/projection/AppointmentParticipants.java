package com.vikrant.careSync.repository.projection;

/** Doctor and patient ids of one appointment, loaded without fetching either entity. */
public record AppointmentParticipants(Long doctorId, Long patientId) {
}
