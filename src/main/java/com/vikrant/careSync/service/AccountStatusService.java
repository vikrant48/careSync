package com.vikrant.careSync.service;

import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.repository.UserRepository;
import com.vikrant.careSync.security.service.RefreshTokenService;
import com.vikrant.careSync.security.service.SecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Activates or deactivates an account everywhere at once: the login account, the doctor or patient profile,
 * and (when deactivating) every session and refresh token. Login is decided by the account flag, so changing
 * only the profile (as "block doctor" / "block patient" used to) left the person able to sign in again.
 */
@Service
@RequiredArgsConstructor
public class AccountStatusService {

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final SecurityService securityService;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public void setActive(Long userId, boolean active) {
        // Loaded here, inside the transaction (a lazy proxy from the caller may belong to a closed session)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
        user.setIsActive(active);
        userRepository.save(user);
        doctorRepository.findById(user.getId()).ifPresent(doctor -> {
            doctor.setIsActive(active);
            doctorRepository.save(doctor);
        });
        patientRepository.findById(user.getId()).ifPresent(patient -> {
            patient.setIsActive(active);
            patientRepository.save(patient);
        });
        if (!active) {
            securityService.deactivateAllUserSessions(user.getUsername());
            refreshTokenService.deleteByUsername(user.getUsername());
        }
    }
}
