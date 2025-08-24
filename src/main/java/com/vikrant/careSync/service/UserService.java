package com.vikrant.careSync.service;

import com.vikrant.careSync.dto.UserDto;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;

    public UserDto getUserByUsername(String username) {
        // First try to find a doctor
        Doctor doctor = doctorRepository.findByUsername(username).orElse(null);
        if (doctor != null) {
            return new UserDto(doctor);
        }

        // If not found as doctor, try to find as patient
        Patient patient = patientRepository.findByUsername(username).orElse(null);
        if (patient != null) {
            return new UserDto(patient);
        }

        throw new RuntimeException("User not found with username: " + username);
    }
}