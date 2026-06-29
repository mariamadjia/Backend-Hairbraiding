package org.example.backendbraiding.controller;

import org.example.backendbraiding.dto.CustomerDetailDTO;
import org.example.backendbraiding.dto.CustomerSummaryDTO;
import org.example.backendbraiding.service.CustomerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@CrossOrigin(origins = "*")
public class CustomerController {
    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<CustomerSummaryDTO>> getAllCustomers() {
        List<CustomerSummaryDTO> customers = customerService.getAllCustomers();
        return ResponseEntity.ok(customers);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<CustomerDetailDTO> getCustomerDetails(@PathVariable Long id) {
        CustomerDetailDTO details = customerService.getCustomerDetails(id);
        return ResponseEntity.ok(details);
    }
}
