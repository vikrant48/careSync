package com.vikrant.careSync.repository.projection;

/** Patient of a lab-test booking and the prescribing doctor (doctor may be null for self-bookings). */
public record BookingOwners(Long patientId, Long doctorId) {
}
