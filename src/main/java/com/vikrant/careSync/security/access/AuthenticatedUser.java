package com.vikrant.careSync.security.access;

import com.vikrant.careSync.entity.User;

/**
 * The logged-in account for the current request. {@code id} is the shared user id,
 * which is also the doctor id or patient id (both entities use @MapsId on users.user_id).
 */
public record AuthenticatedUser(Long id, String username, User.Role role) {

    public boolean isAdmin() {
        return role == User.Role.ADMIN;
    }

    public boolean isDoctor() {
        return role == User.Role.DOCTOR;
    }

    public boolean isPatient() {
        return role == User.Role.PATIENT;
    }
}
