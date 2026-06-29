package org.example.backendbraiding.controller;

import org.example.backendbraiding.model.ChatMessage;
import org.example.backendbraiding.service.ChatMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    
    private final ChatMessageService chatMessageService;
    
    @Autowired
    public ChatController(ChatMessageService chatMessageService) {
        this.chatMessageService = chatMessageService;
    }
    
    private static final String UPLOAD_DIR = "uploads/chat-photos/";
    
    // GET - Get all chat messages
    @GetMapping("/messages")
    public ResponseEntity<List<ChatMessage>> getAllMessages() {
        return ResponseEntity.ok(chatMessageService.getAllMessages());
    }
    
    // GET - Get unread messages
    @GetMapping("/messages/unread")
    public ResponseEntity<List<ChatMessage>> getUnreadMessages() {
        return ResponseEntity.ok(chatMessageService.getUnreadMessages());
    }
    
    // GET - Get message by ID
    @GetMapping("/messages/{id}")
    public ResponseEntity<ChatMessage> getMessageById(@PathVariable Long id) {
        ChatMessage message = chatMessageService.getMessageById(id);
        if (message != null) {
            return ResponseEntity.ok(message);
        }
        return ResponseEntity.notFound().build();
    }
    
    // POST - Send a chat message with optional photo
    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(
            @RequestParam("customerName") String customerName,
            @RequestParam("customerEmail") String customerEmail,
            @RequestParam("customerPhone") String customerPhone,
            @RequestParam("message") String message,
            @RequestParam(value = "photo", required = false) MultipartFile photo
    ) {
        try {
            ChatMessage chatMessage = new ChatMessage(customerName, customerEmail, customerPhone, message);
            
            // Handle photo upload if provided
            if (photo != null && !photo.isEmpty()) {
                String imageUrl = savePhoto(photo);
                chatMessage.setImageUrl(imageUrl);
            }
            
            ChatMessage savedMessage = chatMessageService.saveMessage(chatMessage);
            return ResponseEntity.ok(savedMessage);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to upload photo"));
        }
    }
    
    // PUT - Mark message as read
    @PutMapping("/messages/{id}/read")
    public ResponseEntity<ChatMessage> markAsRead(@PathVariable Long id) {
        ChatMessage message = chatMessageService.markAsRead(id);
        if (message != null) {
            return ResponseEntity.ok(message);
        }
        return ResponseEntity.notFound().build();
    }
    
    // DELETE - Delete message
    @DeleteMapping("/messages/{id}")
    public ResponseEntity<Void> deleteMessage(@PathVariable Long id) {
        chatMessageService.deleteMessage(id);
        return ResponseEntity.ok().build();
    }
    
    // Helper method to save photo
    private String savePhoto(MultipartFile file) throws IOException {
        // Create upload directory if it doesn't exist
        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }
        
        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null ? originalFilename.substring(originalFilename.lastIndexOf(".")) : "";
        String filename = UUID.randomUUID().toString() + extension;
        
        // Save file
        Path filePath = Paths.get(UPLOAD_DIR + filename);
        Files.write(filePath, file.getBytes());
        
        // Return URL
        return "/uploads/chat-photos/" + filename;
    }
}
