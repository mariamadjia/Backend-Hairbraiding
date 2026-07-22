package org.example.backendbraiding.service;

import org.example.backendbraiding.dto.TimeSlotDTO;
import org.example.backendbraiding.model.TimeSlot;
import org.example.backendbraiding.repository.TimeSlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TimeSlotService {
    private final TimeSlotRepository timeSlotRepository;

    public TimeSlotService(TimeSlotRepository timeSlotRepository) {
        this.timeSlotRepository = timeSlotRepository;
    }

    public List<TimeSlotDTO> getTimeSlotsByDay(String dayOfWeek) {
        List<TimeSlot> slots = timeSlotRepository.findByDayOfWeekOrderBySlotOrderAsc(dayOfWeek);
        return slots.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = "availableSlots", allEntries = true)
    public void saveTimeSlotsForDay(String dayOfWeek, List<TimeSlotDTO> slots) {
        DayOfWeek.valueOf(dayOfWeek);
        if (slots == null) throw new IllegalArgumentException("Time slots are required");
        List<TimeSlotDTO> sortedSlots = new ArrayList<>(slots);
        sortedSlots.sort(Comparator.comparing(slot -> LocalTime.parse(slot.getStartTime())));
        LocalTime previousEnd = null;
        for (TimeSlotDTO dto : sortedSlots) {
            LocalTime start = LocalTime.parse(dto.getStartTime());
            LocalTime end = LocalTime.parse(dto.getEndTime());
            if (!end.isAfter(start)) throw new IllegalArgumentException("Slot end must be after its start");
            if (dto.getCapacity() != null && dto.getCapacity() < 1) {
                throw new IllegalArgumentException("Slot capacity must be at least 1");
            }
            if (previousEnd != null && start.isBefore(previousEnd)) {
                throw new IllegalArgumentException("Time slots cannot overlap");
            }
            previousEnd = end;
        }
        // Delete existing slots for this day
        timeSlotRepository.deleteAllByDayOfWeekIn(List.of(dayOfWeek));
        // Ensure replacement rows cannot be inserted before the old unique keys
        // have actually been removed from PostgreSQL.
        timeSlotRepository.flush();
        
        // Save new slots
        for (int i = 0; i < sortedSlots.size(); i++) {
            TimeSlotDTO dto = sortedSlots.get(i);
            TimeSlot slot = new TimeSlot();
            slot.setDayOfWeek(dayOfWeek);
            slot.setStartTime(LocalTime.parse(dto.getStartTime()));
            slot.setEndTime(LocalTime.parse(dto.getEndTime()));
            slot.setCapacity(dto.getCapacity() != null ? dto.getCapacity() : 1);
            slot.setSlotOrder(i);
            timeSlotRepository.save(slot);
        }
    }

    private TimeSlotDTO mapToDTO(TimeSlot slot) {
        TimeSlotDTO dto = new TimeSlotDTO();
        dto.setId(slot.getId());
        dto.setDayOfWeek(slot.getDayOfWeek());
        dto.setStartTime(slot.getStartTime().toString());
        dto.setEndTime(slot.getEndTime().toString());
        dto.setCapacity(slot.getCapacity());
        dto.setSlotOrder(slot.getSlotOrder());
        return dto;
    }
}
