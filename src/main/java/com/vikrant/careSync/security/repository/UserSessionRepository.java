package com.vikrant.careSync.security.repository;

import com.vikrant.careSync.security.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    
    Optional<UserSession> findBySessionIdAndActiveTrue(String sessionId);
    
    List<UserSession> findByUsernameAndActiveTrue(String username);
    
    @Query("SELECT us FROM UserSession us WHERE us.lastActivity < :threshold AND us.active = true")
    List<UserSession> findInactiveSessions(@Param("threshold") Instant threshold);
    
    @Modifying
    @Query("UPDATE UserSession us SET us.lastActivity = :lastActivity WHERE us.sessionId = :sessionId")
    void updateLastActivity(@Param("sessionId") String sessionId, @Param("lastActivity") Instant lastActivity);
    
    @Modifying
    @Query("UPDATE UserSession us SET us.active = false WHERE us.username = :username")
    void deactivateAllSessionsForUser(@Param("username") String username);
} 