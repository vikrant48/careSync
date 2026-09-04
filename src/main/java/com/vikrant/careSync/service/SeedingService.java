package com.vikrant.careSync.service;

import com.vikrant.careSync.dto.SeedBulkResponse;
import com.vikrant.careSync.dto.SeedUserRequest;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.repository.UserRepository;
import com.vikrant.careSync.service.interfaces.ISeedingService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SeedingService implements ISeedingService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final PasswordEncoder passwordEncoder;

    @PersistenceContext
    private final EntityManager entityManager;

    @Override
    @Transactional
    @CacheEvict(value = "PATIENT:PROFILE", allEntries = true)
    public Object seedSingleUser(SeedUserRequest request) {
        Long userId = request != null ? request.getUserId() : null;
        if (userId == null) {
            Long maxExistingId = userRepository.findMaxUserId().orElse(300000L);
            userId = Math.max(300001L, maxExistingId + 1);
        } else if (userRepository.existsById(userId)) {
            throw new RuntimeException("User already exists with ID: " + userId);
        }

        String roleStr = (request != null && request.getRole() != null && !request.getRole().trim().isEmpty())
                ? request.getRole().trim().toUpperCase()
                : "PATIENT";

        User.Role role;
        try {
            role = User.Role.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role: " + roleStr + ". Allowed values: PATIENT, DOCTOR, ADMIN");
        }

        String username = (request != null && request.getUsername() != null && !request.getUsername().trim().isEmpty())
                ? request.getUsername().trim()
                : roleStr.toLowerCase() + "_" + userId;

        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username already exists: " + username);
        }

        String email = (request != null && request.getEmail() != null && !request.getEmail().trim().isEmpty())
                ? request.getEmail().trim()
                : roleStr.toLowerCase() + userId + "@caresync.com";

        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already exists: " + email);
        }

        String rawPassword = (request != null && request.getPassword() != null
                && !request.getPassword().trim().isEmpty())
                        ? request.getPassword()
                        : "demo";

        User user = User.builder()
                .id(userId)
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .email(email)
                .role(role)
                .isActive(true)
                .build();

        User savedUser = userRepository.save(user);

        if (role == User.Role.DOCTOR) {
            Doctor doctor = new Doctor();
            doctor.setUser(savedUser);
            doctor.setFirstName(
                    request != null && request.getFirstName() != null ? request.getFirstName() : "Dr. Rajesh");
            doctor.setLastName(request != null && request.getLastName() != null ? request.getLastName() : "Kumar");
            doctor.setDateOfBirth(request != null && request.getDateOfBirth() != null ? request.getDateOfBirth()
                    : LocalDate.of(1985, 5, 20));
            doctor.setContactInfo(
                    request != null && request.getContactInfo() != null ? request.getContactInfo() : "+91 9876543210");
            doctor.setSpecialization(
                    request != null && request.getSpecialization() != null ? request.getSpecialization()
                            : "General Medicine");
            doctor.setGender(request != null && request.getGender() != null ? request.getGender() : "Male");
            doctor.setConsultationFees(
                    request != null && request.getConsultationFees() != null ? request.getConsultationFees()
                            : new BigDecimal("500.00"));
            doctor.setAddress(request != null && request.getAddress() != null ? request.getAddress()
                    : "CareSync Hospital, Main City");
            doctor.setLanguages(
                    request != null && request.getLanguages() != null ? request.getLanguages() : "English, Hindi");
            doctor.setIsVerified(true);
            doctor.setIsActive(true);
            return doctorRepository.save(doctor);
        } else if (role == User.Role.ADMIN) {
            return savedUser;
        } else {
            Patient patient = new Patient();
            patient.setUser(savedUser);
            patient.setFirstName(request != null && request.getFirstName() != null ? request.getFirstName() : "Rahul");
            patient.setLastName(request != null && request.getLastName() != null ? request.getLastName() : "Sharma");
            patient.setDateOfBirth(request != null && request.getDateOfBirth() != null ? request.getDateOfBirth()
                    : LocalDate.of(1995, 8, 15));
            patient.setContactInfo(
                    request != null && request.getContactInfo() != null ? request.getContactInfo() : "+91 9876543210");
            patient.setIllnessDetails(
                    request != null && request.getIllnessDetails() != null ? request.getIllnessDetails()
                            : "Mild seasonal fever and headache");
            patient.setGender(request != null && request.getGender() != null ? request.getGender() : "Male");
            patient.setBloodGroup(request != null && request.getBloodGroup() != null ? request.getBloodGroup() : "B+");
            patient.setIsActive(true);
            return patientRepository.save(patient);
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = "PATIENT:PROFILE", allEntries = true)
    public SeedBulkResponse seedBulkUsers(List<SeedUserRequest> requests, String defaultPassword) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("Request body must contain a non-empty JSON array of users to seed.");
        }

        long startTime = System.currentTimeMillis();
        String rawPassword = (defaultPassword != null && !defaultPassword.trim().isEmpty()) ? defaultPassword : "demo";
        int BATCH_SIZE = 50;

        int createdCount = 0;
        long minId = Long.MAX_VALUE;
        long maxId = Long.MIN_VALUE;

        for (int i = 0; i < requests.size(); i++) {
            SeedUserRequest req = requests.get(i);
            if (req.getPassword() == null || req.getPassword().trim().isEmpty()) {
                req.setPassword(rawPassword);
            }
            Object created = seedSingleUser(req);
            long id;
            if (created instanceof Patient p) {
                id = p.getUser().getId();
            } else if (created instanceof Doctor d) {
                id = d.getUser().getId();
            } else if (created instanceof User u) {
                id = u.getId();
            } else {
                id = 0L;
            }

            if (id < minId)
                minId = id;
            if (id > maxId)
                maxId = id;
            createdCount++;

            if (i > 0 && i % BATCH_SIZE == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        return SeedBulkResponse.builder()
                .status("SUCCESS")
                .message("Successfully seeded " + createdCount
                        + " user accounts into database tables based on specified roles.")
                .createdCount(createdCount)
                .startUserId(createdCount > 0 ? minId : 0)
                .lastUserId(createdCount > 0 ? maxId : 0)
                .defaultPasswordUsed(rawPassword)
                .executionTimeMs(duration)
                .build();
    }
}
