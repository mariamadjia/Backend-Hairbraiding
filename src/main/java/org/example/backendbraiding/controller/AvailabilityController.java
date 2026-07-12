package org.example.backendbraiding.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.backendbraiding.dto.AvailableSlotDTO;
import org.example.backendbraiding.dto.BlockedTimeSlotDTO;
import org.example.backendbraiding.dto.BusinessHoursDTO;
import org.example.backendbraiding.service.AvailabilityService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/availability")
@RequiredArgsConstructor
@Slf4j
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    // Business Hours Endpoints
    @PostMapping("/business-hours")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BusinessHoursDTO> saveBusinessHours(@RequestBody BusinessHoursDTO dto) {
        BusinessHoursDTO saved = availabilityService.saveBusinessHours(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
    
    @GetMapping("/business-hours")
    public ResponseEntity<List<BusinessHoursDTO>> getAllBusinessHours() {
        List<BusinessHoursDTO> hours = availabilityService.getAllBusinessHours();
        return ResponseEntity.ok(hours);
    }
    
    @GetMapping("/business-hours/{day}")
    public ResponseEntity<BusinessHoursDTO> getBusinessHoursByDay(@PathVariable DayOfWeek day) {
        BusinessHoursDTO hours = availabilityService.getBusinessHoursByDay(day);
        return ResponseEntity.ok(hours);
    }
    
    // Blocked Time Slots Endpoints
    @PostMapping("/block-time")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BlockedTimeSlotDTO> createBlockedSlot(
            @RequestBody BlockedTimeSlotDTO dto,
            Authentication authentication) {
        String adminEmail = authentication.getName();
        BlockedTimeSlotDTO created = availabilityService.createBlockedSlot(dto, adminEmail);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    @GetMapping("/blocked-times")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BlockedTimeSlotDTO>> getBlockedSlots(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        List<BlockedTimeSlotDTO> slots = availabilityService.getBlockedSlots(startDate, endDate);
        return ResponseEntity.ok(slots);
    }
    
    @DeleteMapping("/blocked-times/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteBlockedSlot(@PathVariable Long id) {
        availabilityService.deleteBlockedSlot(id);
        return ResponseEntity.noContent().build();
    }
    
    // Available Slots Endpoint (Public - for customer booking)
    @GetMapping("/slots")
    public ResponseEntity<List<AvailableSlotDTO>> getAvailableSlots(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<AvailableSlotDTO> slots = availabilityService.getAvailableSlots(date);
        return ResponseEntity.ok(slots);
    }
}
