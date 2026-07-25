package org.example.backendbraiding.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.dto.PricingDepositRequest;
import org.example.backendbraiding.dto.PricingBatchRequest;
import org.example.backendbraiding.dto.DefaultDepositPatchRequest;
import org.example.backendbraiding.dto.ServiceDepositPatchRequest;
import org.example.backendbraiding.dto.ClonePricingSizeRequest;
import org.example.backendbraiding.dto.AddPricingLengthRequest;
import org.example.backendbraiding.model.PricingHistory;
import org.example.backendbraiding.model.ServiceItem;
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

    @PatchMapping("/prices")
    public List<ServiceItem> updatePrices(@Valid @RequestBody PricingBatchRequest request) {
        return pricingManagementService.updatePrices(request);
    }

    @PatchMapping("/deposits/default")
    public Map<String, Object> updateDefaultDeposit(@Valid @RequestBody DefaultDepositPatchRequest request) {
        return pricingManagementService.updateDefaultDeposit(request);
    }

    @PatchMapping("/deposits/services/{serviceId}")
    public ServiceItem updateServiceDeposit(@PathVariable Long serviceId,
                                            @Valid @RequestBody ServiceDepositPatchRequest request) {
        return pricingManagementService.updateServiceDeposit(serviceId, request);
    }

    @PostMapping("/sizes/clone")
    public ServiceItem cloneSize(@Valid @RequestBody ClonePricingSizeRequest request) {
        return pricingManagementService.cloneSize(request);
    }

    @PostMapping("/lengths")
    public List<ServiceItem> addLength(@Valid @RequestBody AddPricingLengthRequest request) {
        return pricingManagementService.addLength(request);
    }

    @GetMapping("/history")
    public List<PricingHistory> history(@RequestParam(defaultValue = "100") int limit) {
        return pricingManagementService.history(limit);
    }
}
