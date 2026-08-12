package org.example.backendbraiding.controller;

import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.dto.AddOnResponse;
import org.example.backendbraiding.service.AddOnService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class AddOnPublicController {
    private final AddOnService addOnService;

    @GetMapping("/{serviceId}/add-ons")
    public List<AddOnResponse> available(@PathVariable Long serviceId,
                                         @RequestParam(required = false) Long lengthOptionId) {
        return addOnService.listAvailable(serviceId, lengthOptionId);
    }
}
