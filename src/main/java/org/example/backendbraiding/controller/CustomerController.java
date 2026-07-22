package org.example.backendbraiding.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.backendbraiding.dto.CustomerDetailDTO;
import org.example.backendbraiding.dto.CustomerSummaryDTO;
import org.example.backendbraiding.service.CustomerService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
@Slf4j
public class CustomerController {
    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<CustomerSummaryDTO>> getAllCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "ALL") String segment,
            @RequestParam(defaultValue = "NAME_ASC") String sort) {
        Page<CustomerSummaryDTO> customers = customerService.getAllCustomers(page, size, query, segment, sort);
        return ResponseEntity.ok(customers);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CustomerDetailDTO> getCustomerDetails(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int appointmentPage,
            @RequestParam(defaultValue = "10") int appointmentSize,
            @RequestParam(defaultValue = "ALL") String appointmentStatus) {
        CustomerDetailDTO details = customerService.getCustomerDetails(id, appointmentPage, appointmentSize, appointmentStatus);
        return ResponseEntity.ok(details);
    }
}
