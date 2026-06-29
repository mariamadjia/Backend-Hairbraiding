package org.example.backendbraiding.service;

import org.example.backendbraiding.dto.TimeSlotDTO;
import org.example.backendbraiding.model.TimeSlot;
import org.example.backendbraiding.repository.TimeSlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
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
    public void saveTimeSlotsForDay(String dayOfWeek, List<TimeSlotDTO> slots) {
        // Delete existing slots for this day
        timeSlotRepository.deleteByDayOfWeek(dayOfWeek);
        
        // Save new slots
        for (int i = 0; i < slots.size(); i++) {
            TimeSlotDTO dto = slots.get(i);
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
