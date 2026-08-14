package org.example.backendbraiding.controller;

import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.dto.GuideSettingsDTO;
import org.example.backendbraiding.service.GuideSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class GuideSettingsController {
    private final GuideSettingsService service;
    @GetMapping("/api/guides") public ResponseEntity<GuideSettingsDTO> publicGuides() { return ResponseEntity.ok(service.get()); }
    @GetMapping("/api/admin/guides") public ResponseEntity<GuideSettingsDTO> adminGuides() { return ResponseEntity.ok(service.get()); }
    @PutMapping("/api/admin/guides") public ResponseEntity<GuideSettingsDTO> update(@RequestBody GuideSettingsDTO request) { return ResponseEntity.ok(service.update(request)); }
}
