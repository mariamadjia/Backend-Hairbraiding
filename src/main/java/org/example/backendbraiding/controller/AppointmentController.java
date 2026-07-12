package org.example.backendbraiding.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.dto.AppointmentActionDTO;
import org.example.backendbraiding.dto.AppointmentRequestDTO;
import org.example.backendbraiding.dto.AppointmentResponseDTO;
import org.example.backendbraiding.dto.AppointmentSettingsDTO;
import org.example.backendbraiding.model.Admin;
import org.example.backendbraiding.repository.AdminRepository;
import org.example.backendbraiding.service.AppointmentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final AdminRepository adminRepository;

    @PostMapping
    public ResponseEntity<AppointmentResponseDTO> createAppointment(
            @Valid @RequestBody AppointmentRequestDTO requestDTO) {
        AppointmentResponseDTO response = appointmentService.createAppointment(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AppointmentResponseDTO>> getAllAppointments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "appointmentDateTime") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<AppointmentResponseDTO> appointments = appointmentService.getAllAppointments(pageable);
        return ResponseEntity.ok(appointments);
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AppointmentResponseDTO>> getPendingAppointments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("appointmentDateTime").ascending());
        Page<AppointmentResponseDTO> appointments = appointmentService.getPendingAppointments(pageable);
        return ResponseEntity.ok(appointments);
    }

    @GetMapping("/upcoming")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AppointmentResponseDTO>> getUpcomingAppointments() {
        List<AppointmentResponseDTO> appointments = appointmentService.getUpcomingAppointments();
        return ResponseEntity.ok(appointments);
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AppointmentResponseDTO>> getAppointmentsByStatus(
            @PathVariable String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("appointmentDateTime").descending());
        Page<AppointmentResponseDTO> appointments = appointmentService.getAppointmentsByStatus(status, pageable);
        return ResponseEntity.ok(appointments);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AppointmentResponseDTO> getAppointmentById(@PathVariable Long id) {
        AppointmentResponseDTO appointment = appointmentService.getAppointmentById(id);
        return ResponseEntity.ok(appointment);
    }

    @GetMapping("/date-range")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AppointmentResponseDTO>> getAppointmentsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        List<AppointmentResponseDTO> appointments = appointmentService.getAppointmentsByDateRange(startDate, endDate);
        return ResponseEntity.ok(appointments);
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AppointmentResponseDTO> approveAppointment(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AppointmentActionDTO actionDTO,
            Authentication authentication) {
        
        Long adminId = extractAdminId(authentication);
        AppointmentActionDTO dto = actionDTO != null ? actionDTO : new AppointmentActionDTO();
        
        AppointmentResponseDTO response = appointmentService.approveAppointment(id, adminId, dto);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/deny")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AppointmentResponseDTO> denyAppointment(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AppointmentActionDTO actionDTO,
            Authentication authentication) {
        
        Long adminId = extractAdminId(authentication);
        AppointmentActionDTO dto = actionDTO != null ? actionDTO : new AppointmentActionDTO();
        
        AppointmentResponseDTO response = appointmentService.denyAppointment(id, adminId, dto);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AppointmentSettingsDTO> getSettings() {
        AppointmentSettingsDTO settings = appointmentService.getSettings();
        return ResponseEntity.ok(settings);
    }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AppointmentSettingsDTO> updateSettings(
            @Valid @RequestBody AppointmentSettingsDTO dto,
            Authentication authentication) {
        Long adminId = extractAdminId(authentication);
        AppointmentSettingsDTO updated = appointmentService.updateSettings(dto, adminId);
        return ResponseEntity.ok(updated);
    }

    private Long extractAdminId(Authentication authentication) {
        String email = authentication.getName();
        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        return admin.getId();
    }
}
