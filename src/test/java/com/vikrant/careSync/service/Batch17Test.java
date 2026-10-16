package com.vikrant.careSync.service;

import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.UserRepository;
import com.vikrant.careSync.scheduler.ClusterJobLock;
import com.vikrant.careSync.security.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** User ids from a sequence, one-instance job lock, and blocking an account blocks its login. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class Batch17Test {

    @Autowired
    private UserIdGenerator userIdGenerator;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private ClusterJobLock clusterJobLock;
    @Autowired
    private AccountStatusService accountStatusService;
    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Test
    void userIdsAreUniqueAndSkipTakenNumbers() {
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            assertTrue(ids.add(userIdGenerator.next()), "every call gives a new id");
        }
        Long next = userIdGenerator.next();
        // An account created with an explicit id (e.g. seeding) must not be handed out again
        userRepository.save(User.builder().id(next + 1).username("seeded").password("x")
                .email("seeded@test.local").role(User.Role.PATIENT).build());
        assertNotEquals(next + 1, userIdGenerator.next());
    }

    @Test
    void jobLockAlwaysRunsOnNonPostgresDatabases() {
        assertTrue(clusterJobLock.tryAcquire("appointment-reminders"));
    }

    @Test
    void blockingADoctorBlocksTheLoginAccount() {
        Doctor doctor = new Doctor();
        doctor.setUser(User.builder().id(9801L).username("doctorBlock").password("x")
                .email("doctorBlock@test.local").role(User.Role.DOCTOR).isActive(true).build());
        doctor.setFirstName("Doc");
        doctor.setLastName("Block");
        doctorRepository.save(doctor);

        accountStatusService.setActive(9801L, false);

        assertFalse(userDetailsService.loadUserByUsername("doctorBlock").isEnabled(),
                "a blocked doctor can no longer sign in");
        assertFalse(doctorRepository.findById(9801L).orElseThrow().getIsActive());

        accountStatusService.setActive(9801L, true);
        assertTrue(userDetailsService.loadUserByUsername("doctorBlock").isEnabled());
    }
}
