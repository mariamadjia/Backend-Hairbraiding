package org.example.backendbraiding.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.dto.PricingDepositRequest;
import org.example.backendbraiding.model.PricingHistory;
import org.example.backendbraiding.service.PricingManagementService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/pricing")
@RequiredArgsConstructor
public class PricingManagementController {
    private final PricingManagementService pricingManagementService;

    @GetMapping("/deposits")
    public Map<String, Object> deposits() {
        return pricingManagementService.deposits();
    }

    @PutMapping("/deposits")
    public Map<String, Object> updateDeposits(@Valid @RequestBody PricingDepositRequest request) {
        return pricingManagementService.updateDeposits(request);
    }

    @GetMapping("/history")
    public List<PricingHistory> history(@RequestParam(defaultValue = "100") int limit) {
        return pricingManagementService.history(limit);
    }
}
