package com.vikrant.careSync.security;

import com.vikrant.careSync.constants.AppConstants;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // First try to find a doctor
        Doctor doctor = doctorRepository.findByUsername(username).orElse(null);
        if (doctor != null) {
            return new User(
                    doctor.getUsername(),
                    doctor.getPassword(),
                    Collections.singletonList(new SimpleGrantedAuthority(AppConstants.Roles.ROLE_DOCTOR)));
        }

        // If not found as doctor, try to find as patient
        Patient patient = patientRepository.findByUsername(username).orElse(null);
        if (patient != null) {
            return new User(
                    patient.getUsername(),
                    patient.getPassword(),
                    Collections.singletonList(new SimpleGrantedAuthority(AppConstants.Roles.ROLE_PATIENT)));
        }

        throw new UsernameNotFoundException("User not found with username: " + username);
    }
}