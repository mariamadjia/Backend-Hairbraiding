package org.example.backendbraiding.controller;

import org.example.backendbraiding.model.ChatMessage;
import org.example.backendbraiding.service.ChatMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    
    private final ChatMessageService chatMessageService;
    
    @Autowired
    public ChatController(ChatMessageService chatMessageService) {
        this.chatMessageService = chatMessageService;
    }
    
    private static final long MAX_PHOTO_BYTES = 10L * 1024L * 1024L;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Path UPLOAD_DIR = Paths.get(
            System.getenv("UPLOAD_DIR") != null ? System.getenv("UPLOAD_DIR") : "public",
            "uploads",
            "chat-photos"
    ).normalize();
    
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
        String validationError = validateMessage(customerName, customerEmail, customerPhone, message, photo);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError));
        }

        try {
            ChatMessage chatMessage = new ChatMessage(
                    customerName.trim(),
                    customerEmail.trim(),
                    customerPhone.trim(),
                    message.trim()
            );
            
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
        Files.createDirectories(UPLOAD_DIR);
        String extension = extensionFor(file.getContentType());
        String filename = UUID.randomUUID() + extension;
        Path filePath = UPLOAD_DIR.resolve(filename).normalize();
        if (!filePath.startsWith(UPLOAD_DIR)) {
            throw new IOException("Invalid upload path");
        }
        file.transferTo(filePath);
        return "/uploads/chat-photos/" + filename;
    }

    private String validateMessage(String name, String email, String phone, String message, MultipartFile photo) {
        if (isBlank(name) || isBlank(email) || isBlank(phone) || isBlank(message)) {
            return "Name, email, phone, and message are required";
        }
        if (name.trim().length() > 100) return "Name must be 100 characters or fewer";
        if (email.trim().length() > 100 || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            return "Enter a valid email address";
        }
        if (phone.trim().length() > 20) return "Phone number must be 20 characters or fewer";
        if (message.trim().length() > 5000) return "Message must be 5000 characters or fewer";
        if (photo != null && !photo.isEmpty()) {
            if (photo.getSize() > MAX_PHOTO_BYTES) return "Photo must be 10MB or smaller";
            if (extensionFor(photo.getContentType()) == null) return "Photo must be a JPG, PNG, WebP, GIF, or HEIC image";
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String extensionFor(String contentType) {
        if (contentType == null) return null;
        return switch (contentType.toLowerCase()) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "image/heic", "image/heif" -> ".heic";
            default -> null;
        };
    }
}
