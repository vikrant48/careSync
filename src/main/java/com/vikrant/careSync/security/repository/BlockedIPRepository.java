package com.vikrant.careSync.security.repository;

import com.vikrant.careSync.security.entity.BlockedIP;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface BlockedIPRepository extends JpaRepository<BlockedIP, Long> {
    
    Optional<BlockedIP> findByIpAddressAndActiveTrue(String ipAddress);
    
    @Query("SELECT bip FROM BlockedIP bip WHERE bip.expiresAt < :now AND bip.active = true")
    List<BlockedIP> findExpiredBlockedIPs(@org.springframework.data.repository.query.Param("now") Instant now);
    
    List<BlockedIP> findByActiveTrue();
} 