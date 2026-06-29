package org.example.backendbraiding.service;

import org.example.backendbraiding.model.ChatMessage;
import org.example.backendbraiding.repository.ChatMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChatMessageService {
    
    private final ChatMessageRepository chatMessageRepository;
    
    @Autowired
    public ChatMessageService(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }
    
    public List<ChatMessage> getAllMessages() {
        return chatMessageRepository.findAllByOrderByCreatedAtDesc();
    }
    
    public List<ChatMessage> getUnreadMessages() {
        return chatMessageRepository.findByIsReadFalse();
    }
    
    public ChatMessage getMessageById(Long id) {
        return chatMessageRepository.findById(id).orElse(null);
    }
    
    @Transactional
    public ChatMessage saveMessage(ChatMessage message) {
        return chatMessageRepository.save(message);
    }
    
    @Transactional
    public ChatMessage markAsRead(Long id) {
        ChatMessage message = chatMessageRepository.findById(id).orElse(null);
        if (message != null) {
            message.setRead(true);
            return chatMessageRepository.save(message);
        }
        return null;
    }
    
    @Transactional
    public void deleteMessage(Long id) {
        chatMessageRepository.deleteById(id);
    }
}
