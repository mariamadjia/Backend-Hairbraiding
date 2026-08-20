package org.example.backendbraiding.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.dto.AppointmentActionDTO;
import org.example.backendbraiding.dto.AppointmentRequestDTO;
import org.example.backendbraiding.dto.AppointmentResponseDTO;
import org.example.backendbraiding.dto.AppointmentSettingsDTO;
import org.example.backendbraiding.dto.AppointmentEventDTO;
import org.example.backendbraiding.dto.NoShowChargeRequest;
import org.example.backendbraiding.dto.NoShowFeeDTO;
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
import java.util.Map;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final AdminRepository adminRepository;
    private final org.example.backendbraiding.service.AppointmentEventService appointmentEventService;
    private final org.example.backendbraiding.service.NoShowService noShowService;

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
        Sort sort = appointmentSort(sortBy, sortDir);
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<AppointmentResponseDTO> appointments = appointmentService.getAllAppointments(pageable);
        return ResponseEntity.ok(appointments);
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AppointmentResponseDTO>> getPendingAppointments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "appointmentDateTime") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Pageable pageable = PageRequest.of(page, size, appointmentSort(sortBy, sortDir));
        Page<AppointmentResponseDTO> appointments = appointmentService.getPendingAppointments(pageable);
        return ResponseEntity.ok(appointments);
    }

    @GetMapping("/upcoming")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AppointmentResponseDTO>> getUpcomingAppointments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        int boundedSize = Math.min(Math.max(size, 1), 200);
        Page<AppointmentResponseDTO> appointments = appointmentService.getUpcomingAppointments(
                PageRequest.of(Math.max(page, 0), boundedSize, Sort.by("appointmentDateTime").ascending()));
        return ResponseEntity.ok(appointments);
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AppointmentResponseDTO>> getAppointmentsByStatus(
            @PathVariable String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "appointmentDateTime") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Pageable pageable = PageRequest.of(page, size, appointmentSort(sortBy, sortDir));
        Page<AppointmentResponseDTO> appointments = appointmentService.getAppointmentsByStatus(status, pageable);
        return ResponseEntity.ok(appointments);
    }

    @GetMapping("/workflow")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AppointmentResponseDTO>> getWorkflowAppointments(
            @RequestParam(defaultValue = "NEEDS_ACTION") String view,
            @RequestParam(defaultValue = "ALL") String detail,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "appointmentDateTime") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Pageable pageable = PageRequest.of(page, size, appointmentSort(sortBy, sortDir));
        return ResponseEntity.ok(appointmentService.getWorkflowAppointments(view, detail, q, pageable));
    }

    @GetMapping("/workflow-counts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Long>> getWorkflowCounts() {
        return ResponseEntity.ok(appointmentService.getWorkflowCounts());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AppointmentResponseDTO> getAppointmentById(@PathVariable Long id) {
        AppointmentResponseDTO appointment = appointmentService.getAppointmentById(id);
        return ResponseEntity.ok(appointment);
    }

    @GetMapping("/{id}/events")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AppointmentEventDTO>> getAppointmentEvents(@PathVariable Long id) {
        appointmentService.getAppointmentById(id);
        return ResponseEntity.ok(appointmentEventService.history(id));
    }

    @GetMapping("/date-range")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AppointmentResponseDTO>> getAppointmentsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        int boundedSize = Math.min(Math.max(size, 1), 200);
        Page<AppointmentResponseDTO> appointments = appointmentService.getAppointmentsByDateRange(
                startDate, endDate, PageRequest.of(Math.max(page, 0), boundedSize,
                        Sort.by("appointmentDateTime").ascending()));
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

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AppointmentResponseDTO> completeAppointment(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AppointmentActionDTO actionDTO,
            Authentication authentication) {
        return ResponseEntity.ok(appointmentService.completeAppointment(
                id, extractAdminId(authentication),
                actionDTO != null ? actionDTO : new AppointmentActionDTO()));
    }

    @PostMapping("/{id}/no-show")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NoShowFeeDTO> markNoShow(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) NoShowChargeRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(noShowService.markAndCharge(id, extractAdminId(authentication),
                request == null ? new NoShowChargeRequest() : request));
    }

    @PostMapping("/{id}/no-show/retry")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NoShowFeeDTO> retryNoShowCharge(@PathVariable Long id,
            @Valid @RequestBody(required = false) NoShowChargeRequest request) {
        return ResponseEntity.ok(noShowService.retryCharge(id,
                request == null ? new NoShowChargeRequest() : request));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AppointmentResponseDTO> cancelAppointment(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AppointmentActionDTO actionDTO,
            Authentication authentication) {
        return ResponseEntity.ok(appointmentService.cancelAppointment(
                id, extractAdminId(authentication),
                actionDTO != null ? actionDTO : new AppointmentActionDTO()));
    }

    @PostMapping("/{id}/retry-notification")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AppointmentResponseDTO> retryNotification(@PathVariable Long id) {
        return ResponseEntity.ok(appointmentService.retryNotification(id));
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
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new org.springframework.security.access.AccessDeniedException("Administrator authentication is required");
        }
        String email = authentication.getName();
        Admin admin = adminRepository.findByEmailIgnoreCase(email)
                .filter(candidate -> "ACTIVE".equalsIgnoreCase(candidate.getStatus()))
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException(
                        "The authenticated administrator account is unavailable"));
        return admin.getId();
    }

    private Sort appointmentSort(String sortBy, String sortDir) {
        String property = switch (sortBy) {
            case "createdAt", "paymentStatus", "appointmentDateTime" -> sortBy;
            default -> "appointmentDateTime";
        };
        return sortDir.equalsIgnoreCase("asc") ? Sort.by(property).ascending() : Sort.by(property).descending();
    }
}
