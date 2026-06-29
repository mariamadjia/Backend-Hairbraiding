package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    
    List<ChatMessage> findByIsReadFalse();
    
    List<ChatMessage> findAllByOrderByCreatedAtDesc();
}
