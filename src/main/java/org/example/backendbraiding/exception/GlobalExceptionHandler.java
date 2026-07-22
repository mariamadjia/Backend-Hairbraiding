package org.example.backendbraiding.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("AccessDeniedException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(errorBody("Access denied"));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        log.warn("ResourceNotFoundException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(errorBody(ex.getMessage()));
    }

    @ExceptionHandler(SlugAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleSlugExists(SlugAlreadyExistsException ex) {
        log.warn("SlugAlreadyExistsException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(errorBody(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        log.warn("Validation failed: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorBody(message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException ex) {
        HttpStatus status = ex.getMessage() != null && ex.getMessage().toLowerCase().contains("expired")
                ? HttpStatus.GONE : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(errorBody(ex.getMessage()));
    }

    @ExceptionHandler(PaymentProcessingException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentProvider(PaymentProcessingException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorBody(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody("Internal server error"));
    }

    private Map<String, Object> errorBody(String message) {
        return Map.of(
                "error", message,
                "timestamp", LocalDateTime.now().toString()
        );
    }
}
