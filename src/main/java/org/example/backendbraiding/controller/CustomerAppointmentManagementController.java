package org.example.backendbraiding.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.dto.AvailableSlotDTO;
import org.example.backendbraiding.dto.CustomerCancelRequest;
import org.example.backendbraiding.dto.CustomerRescheduleRequest;
import org.example.backendbraiding.dto.ManagedAppointmentDTO;
import org.example.backendbraiding.service.CustomerAppointmentManagementService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/public/appointments/manage")
@RequiredArgsConstructor
public class CustomerAppointmentManagementController {
    private final CustomerAppointmentManagementService service;

    @GetMapping("/{token}")
    public ResponseEntity<ManagedAppointmentDTO> get(@PathVariable String token) {
        return ResponseEntity.ok(service.get(token));
    }

    @GetMapping("/{token}/slots")
    public ResponseEntity<List<AvailableSlotDTO>> slots(
            @PathVariable String token,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.slots(token, date));
    }

    @PostMapping("/{token}/reschedule")
    public ResponseEntity<ManagedAppointmentDTO> reschedule(
            @PathVariable String token, @Valid @RequestBody CustomerRescheduleRequest request) {
        return ResponseEntity.ok(service.reschedule(token, request));
    }

    @PostMapping("/{token}/cancel")
    public ResponseEntity<ManagedAppointmentDTO> cancel(
            @PathVariable String token, @Valid @RequestBody(required = false) CustomerCancelRequest request) {
        return ResponseEntity.ok(service.cancel(token, request == null ? new CustomerCancelRequest() : request));
    }
}
