package org.example.backendbraiding.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private static final Logger log = LoggerFactory.getLogger(AdminController.class);
    private static final String UPLOAD_DIR = System.getenv("UPLOAD_DIR") != null 
        ? System.getenv("UPLOAD_DIR") 
        : "public";
    private static final String BASE_URL = System.getenv("BASE_URL") != null 
        ? System.getenv("BASE_URL") 
        : "http://localhost:8080";

    @PostMapping("/upload")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file,
                                                             javax.servlet.http.HttpServletRequest request) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
            }

            // Validate file type
            String contentType = file.getContentType();
            if (contentType == null || (!contentType.startsWith("image/"))) {
                return ResponseEntity.badRequest().body(Map.of("error", "Only image files are allowed"));
            }

            // Create upload directory if it doesn't exist
            String uploadDir = UPLOAD_DIR;
            if (!uploadDir.endsWith("/")) {
                uploadDir += "/";
            }
            uploadDir += "uploads/";
            
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".") 
                ? originalFilename.substring(originalFilename.lastIndexOf(".")) 
                : "";
            String filename = UUID.randomUUID().toString() + extension;
            
            Path filePath = uploadPath.resolve(filename).normalize();
            Files.copy(file.getInputStream(), filePath);

            // Construct full URL - check various headers for proxy setups
            String baseUrl = BASE_URL;
            if (baseUrl.equals("http://localhost:8080")) {
                // Try to get from various headers (Render, Nginx, etc.)
                String forwardedHost = request.getHeader("X-Forwarded-Host");
                String forwardedProto = request.getHeader("X-Forwarded-Proto");
                String host = request.getHeader("Host");
                
                if (forwardedHost != null && forwardedProto != null) {
                    baseUrl = forwardedProto + "://" + forwardedHost;
                } else if (host != null) {
                    String scheme = request.getScheme();
                    baseUrl = scheme + "://" + host;
                } else {
                    // Final fallback to request
                    String scheme = request.getScheme();
                    String serverName = request.getServerName();
                    int serverPort = request.getServerPort();
                    baseUrl = scheme + "://" + serverName;
                    if ((scheme.equals("http") && serverPort != 80) || (scheme.equals("https") && serverPort != 443)) {
                        baseUrl += ":" + serverPort;
                    }
                }
            }
            String fileUrl = baseUrl + "/uploads/" + filename;
            log.info("File uploaded successfully: {}", fileUrl);
            
            return ResponseEntity.ok(Map.of("url", fileUrl));
        } catch (IOException e) {
            log.error("Failed to upload file", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to upload file"));
        }
    }
}
