package org.example.backendbraiding.controller;

import jakarta.validation.Valid;
import org.example.backendbraiding.dto.ServiceItemResponse;
import org.example.backendbraiding.dto.ServiceOrderRequest;
import org.example.backendbraiding.service.ServiceItemService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/services")
public class ServiceAdminController {
    private final ServiceItemService serviceItemService;

    public ServiceAdminController(ServiceItemService serviceItemService) {
        this.serviceItemService = serviceItemService;
    }

    @GetMapping("/archived")
    public List<ServiceItemResponse> archived(@RequestParam(required = false) Long subcategoryId) {
        return serviceItemService.getArchivedServices(subcategoryId).stream().map(ServiceItemResponse::from).toList();
    }

    @PostMapping("/{id}/restore")
    public ServiceItemResponse restore(@PathVariable Long id) {
        return ServiceItemResponse.from(serviceItemService.restoreService(id));
    }

    @PutMapping("/reorder")
    public Map<String, Boolean> reorder(@Valid @RequestBody ServiceOrderRequest request) {
        serviceItemService.reorderServices(request.getServiceIds());
        return Map.of("success", true);
    }
}
