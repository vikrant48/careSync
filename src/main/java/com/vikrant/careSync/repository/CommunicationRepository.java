package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.Communication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunicationRepository extends JpaRepository<Communication, Long> {
}