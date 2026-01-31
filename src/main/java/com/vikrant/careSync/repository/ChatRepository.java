package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByAppointmentIdOrderByTimestampAsc(Long appointmentId);

    void deleteByAppointmentId(Long appointmentId);
}
