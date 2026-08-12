package org.example.backendbraiding.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.dto.AddOnAssignmentRequest;
import org.example.backendbraiding.dto.AddOnRequest;
import org.example.backendbraiding.dto.AddOnResponse;
import org.example.backendbraiding.service.AddOnService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/add-ons")
@RequiredArgsConstructor
public class AddOnAdminController {
    private final AddOnService addOnService;

    @GetMapping("/subcategory/{subcategoryId}")
    public List<AddOnResponse> list(@PathVariable Long subcategoryId) {
        return addOnService.listForSubcategory(subcategoryId);
    }

    @PostMapping
    public ResponseEntity<List<AddOnResponse>> create(@Valid @RequestBody AddOnRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(addOnService.create(request));
    }

    @PutMapping("/{addOnId}")
    public Map<String, Boolean> update(@PathVariable Long addOnId, @Valid @RequestBody AddOnRequest request) {
        addOnService.updateDefinition(addOnId, request);
        return Map.of("success", true);
    }

    @DeleteMapping("/{addOnId}")
    public Map<String, Boolean> archive(@PathVariable Long addOnId) {
        addOnService.archive(addOnId);
        return Map.of("success", true);
    }

    @PostMapping("/subcategory/{subcategoryId}/{addOnId}")
    public AddOnResponse assign(@PathVariable Long subcategoryId, @PathVariable Long addOnId,
                                @Valid @RequestBody AddOnAssignmentRequest request) {
        return addOnService.assign(subcategoryId, addOnId, request);
    }

    @PutMapping("/assignments/{assignmentId}")
    public AddOnResponse updateAssignment(@PathVariable Long assignmentId,
                                          @Valid @RequestBody AddOnAssignmentRequest request) {
        return addOnService.updateAssignment(assignmentId, request);
    }

    @DeleteMapping("/assignments/{assignmentId}")
    public Map<String, Boolean> removeAssignment(@PathVariable Long assignmentId) {
        addOnService.removeAssignment(assignmentId);
        return Map.of("success", true);
    }

    @PostMapping("/subcategory/{subcategoryId}/reorder")
    public Map<String, Boolean> reorder(@PathVariable Long subcategoryId, @RequestBody List<Long> assignmentIds) {
        addOnService.reorder(subcategoryId, assignmentIds);
        return Map.of("success", true);
    }
}
