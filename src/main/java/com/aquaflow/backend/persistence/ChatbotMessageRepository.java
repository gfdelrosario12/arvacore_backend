package com.aquaflow.backend.persistence;

import com.aquaflow.backend.entity.ChatbotMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatbotMessageRepository extends JpaRepository<ChatbotMessage, Long> {
    List<ChatbotMessage> findBySessionIdOrderByTimestampAsc(String sessionId);
}

