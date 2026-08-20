package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Override
    Optional<User> findById(Long id);

    @Override
    boolean existsById(Long id);
}
