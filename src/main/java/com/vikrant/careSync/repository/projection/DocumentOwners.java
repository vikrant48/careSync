package com.vikrant.careSync.repository.projection;

/** Owning patient and/or doctor of one document; either id may be null. */
public record DocumentOwners(Long patientId, Long doctorId) {
}
