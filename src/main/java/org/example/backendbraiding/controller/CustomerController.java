package org.example.backendbraiding.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.backendbraiding.dto.CustomerDetailDTO;
import org.example.backendbraiding.dto.CustomerSummaryDTO;
import org.example.backendbraiding.service.CustomerService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@CrossOrigin(origins = "*")
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
            @RequestParam(defaultValue = "lastName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            Authentication authentication) {
        log.info("getAllCustomers called - User: {}, Authorities: {}", 
            authentication.getName(), authentication.getAuthorities());
        
        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<CustomerSummaryDTO> customers = customerService.getAllCustomers(pageable);
        return ResponseEntity.ok(customers);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CustomerDetailDTO> getCustomerDetails(@PathVariable Long id) {
        CustomerDetailDTO details = customerService.getCustomerDetails(id);
        return ResponseEntity.ok(details);
    }
}
