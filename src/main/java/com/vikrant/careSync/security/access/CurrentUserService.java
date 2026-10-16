package com.vikrant.careSync.security.access;

import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Optional;

/**
 * Single place to resolve "who is calling". Replaces the getCurrentPatient/getCurrentDoctor
 * helpers that were copied into several controllers. Error messages are kept identical to
 * the old helpers so API responses do not change.
 */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private static final String REQUEST_CACHE_KEY = CurrentUserService.class.getName() + ".currentUser";

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;

    public Optional<String> currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.ofNullable(authentication.getName());
    }

    /**
     * The current account, loaded once per HTTP request (several access checks can run per request).
     */
    public Optional<AuthenticatedUser> current() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes != null) {
            Object cached = requestAttributes.getAttribute(REQUEST_CACHE_KEY, RequestAttributes.SCOPE_REQUEST);
            if (cached instanceof AuthenticatedUser user) {
                return Optional.of(user);
            }
        }

        Optional<AuthenticatedUser> user = currentUsername()
                .flatMap(userRepository::findByUsername)
                .map(u -> new AuthenticatedUser(u.getId(), u.getUsername(), u.getRole()));

        if (requestAttributes != null) {
            user.ifPresent(u -> requestAttributes.setAttribute(REQUEST_CACHE_KEY, u, RequestAttributes.SCOPE_REQUEST));
        }
        return user;
    }

    public AuthenticatedUser require() {
        return current().orElseThrow(() -> new RuntimeException("User not authenticated"));
    }

    public Patient requirePatient() {
        String username = currentUsername().orElseThrow(() -> new RuntimeException("User not authenticated"));
        return patientRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Patient not found with username: " + username));
    }

    public Doctor requireDoctor() {
        String username = currentUsername().orElseThrow(() -> new RuntimeException("User not authenticated"));
        return doctorRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Doctor not found with username: " + username));
    }
}
