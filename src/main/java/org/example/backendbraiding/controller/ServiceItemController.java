package org.example.backendbraiding.controller;

import jakarta.validation.Valid;
import org.example.backendbraiding.dto.ServiceItemRequest;
import org.example.backendbraiding.dto.ServiceItemResponse;
import org.example.backendbraiding.model.ServiceItem;
import org.example.backendbraiding.service.ServiceItemService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/services")
public class ServiceItemController {
    private final ServiceItemService serviceItemService;

    public ServiceItemController(ServiceItemService serviceItemService) {
        this.serviceItemService = serviceItemService;
    }

    @GetMapping public List<ServiceItemResponse> getAllServices() { return serviceItemService.getAllServices().stream().map(ServiceItemResponse::from).toList(); }
    @GetMapping("/{id}") public ServiceItemResponse getServiceById(@PathVariable Long id) { return ServiceItemResponse.from(serviceItemService.getServiceById(id)); }
    @GetMapping("/category/{categoryId}") public List<ServiceItemResponse> getServicesByCategory(@PathVariable Long categoryId) { return serviceItemService.getServicesByCategory(categoryId).stream().map(ServiceItemResponse::from).toList(); }
    @GetMapping("/subcategory/{subcategoryId}") public List<ServiceItemResponse> getServicesBySubcategory(@PathVariable Long subcategoryId) { return serviceItemService.getServicesBySubcategory(subcategoryId).stream().map(ServiceItemResponse::from).toList(); }

    @PostMapping
    public ResponseEntity<ServiceItemResponse> createService(@Valid @RequestBody ServiceItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ServiceItemResponse.from(serviceItemService.createService(request)));
    }

    @PutMapping("/{id}")
    public ServiceItemResponse updateService(@PathVariable Long id, @Valid @RequestBody ServiceItemRequest request) {
        return ServiceItemResponse.from(serviceItemService.updateService(id, request));
    }

    @DeleteMapping("/{id}")
    public Map<String, String> deleteService(@PathVariable Long id) {
        serviceItemService.deleteService(id);
        return Map.of("message", "Service archived successfully");
    }
}
