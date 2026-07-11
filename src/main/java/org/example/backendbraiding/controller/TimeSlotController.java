package org.example.backendbraiding.controller;

import org.example.backendbraiding.dto.TimeSlotDTO;
import org.example.backendbraiding.service.TimeSlotService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/time-slots")
@CrossOrigin(origins = "*")
public class TimeSlotController {
    private final TimeSlotService timeSlotService;

    public TimeSlotController(TimeSlotService timeSlotService) {
        this.timeSlotService = timeSlotService;
    }

    @GetMapping("/{dayOfWeek}")
    public ResponseEntity<List<TimeSlotDTO>> getTimeSlotsByDay(@PathVariable String dayOfWeek) {
        List<TimeSlotDTO> slots = timeSlotService.getTimeSlotsByDay(dayOfWeek);
        return ResponseEntity.ok(slots);
    }
    
    @GetMapping
    public ResponseEntity<List<TimeSlotDTO>> getAllTimeSlots() {
        // This endpoint is not used but added for completeness
        return ResponseEntity.ok(List.of());
    }

    @PostMapping("/{dayOfWeek}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> saveTimeSlotsForDay(
            @PathVariable String dayOfWeek,
            @RequestBody List<TimeSlotDTO> slots) {
        timeSlotService.saveTimeSlotsForDay(dayOfWeek, slots);
        return ResponseEntity.ok().build();
    }
}
