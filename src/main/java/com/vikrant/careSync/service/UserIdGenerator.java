package com.vikrant.careSync.service;

import com.vikrant.careSync.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * New user ids from the database sequence user_id_seq (Flyway V9), which hands out each number once even with
 * concurrent sign-ups and several app instances. Replaces "SELECT MAX(id) + 1", where two sign-ups at the same
 * moment received the same id and one of them failed.
 */
@Component
@RequiredArgsConstructor
public class UserIdGenerator {

    private static final int MAX_ATTEMPTS = 50;

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;

    public Long next() {
        // Skips numbers already taken by accounts created with an explicit id (e.g. seeding)
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Long id = jdbcTemplate.queryForObject("SELECT nextval('user_id_seq')", Long.class);
            if (id != null && !userRepository.existsById(id)) {
                return id;
            }
        }
        throw new IllegalStateException("Could not allocate a new user id");
    }
}
