package com.vikrant.careSync.service;

import com.vikrant.careSync.dto.UserDto;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.UserRepository;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;

    public UserDto getUserByUsername(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user != null) {
            if (user.getRole() == User.Role.DOCTOR) {
                var doctor = doctorRepository.findById(user.getId()).orElse(null);
                if (doctor != null)
                    return new UserDto(doctor);
            } else if (user.getRole() == User.Role.PATIENT) {
                var patient = patientRepository.findById(user.getId()).orElse(null);
                if (patient != null)
                    return new UserDto(patient);
            }
            return new UserDto(user);
        }

        throw new RuntimeException("User not found with username: " + username);
    }
}