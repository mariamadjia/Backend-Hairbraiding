package org.example.backendbraiding.service;

import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.dto.AvailableSlotDTO;
import org.example.backendbraiding.dto.BlockedTimeSlotDTO;
import org.example.backendbraiding.dto.BusinessHoursDTO;
import org.example.backendbraiding.model.*;
import org.example.backendbraiding.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AvailabilityService {
    
    private final BusinessHoursRepository businessHoursRepository;
    private final BlockedTimeSlotRepository blockedTimeSlotRepository;
    private final AppointmentSettingsRepository settingsRepository;
    private final AppointmentRepository appointmentRepository;
    private final AdminRepository adminRepository;
    
    // Business Hours Management
    @Transactional
    public BusinessHoursDTO saveBusinessHours(BusinessHoursDTO dto) {
        BusinessHours hours = businessHoursRepository.findByDayOfWeek(dto.getDayOfWeek())
            .orElse(new BusinessHours());
        
        hours.setDayOfWeek(dto.getDayOfWeek());
        hours.setOpenTime(dto.getOpenTime());
        hours.setCloseTime(dto.getCloseTime());
        hours.setIsOpen(dto.getIsOpen());
        hours.setNotes(dto.getNotes());
        
        hours = businessHoursRepository.save(hours);
        return mapToBusinessHoursDTO(hours);
    }
    
    public List<BusinessHoursDTO> getAllBusinessHours() {
        return businessHoursRepository.findAll().stream()
            .map(this::mapToBusinessHoursDTO)
            .collect(Collectors.toList());
    }
    
    public BusinessHoursDTO getBusinessHoursByDay(DayOfWeek day) {
        return businessHoursRepository.findByDayOfWeek(day)
            .map(this::mapToBusinessHoursDTO)
            .orElse(createDefaultBusinessHours(day));
    }
    
    // Blocked Time Slots Management
    @Transactional
    public BlockedTimeSlotDTO createBlockedSlot(BlockedTimeSlotDTO dto, String adminEmail) {
        Admin admin = adminRepository.findByEmail(adminEmail)
            .orElseThrow(() -> new RuntimeException("Admin not found"));
        
        BlockedTimeSlot slot = new BlockedTimeSlot();
        slot.setStartDateTime(dto.getStartDateTime());
        slot.setEndDateTime(dto.getEndDateTime());
        slot.setReason(dto.getReason());
        slot.setIsRecurring(dto.getIsRecurring());
        slot.setRecurrencePattern(dto.getRecurrencePattern());
        slot.setCreatedBy(admin);
        slot.setCreatedAt(LocalDateTime.now());
        
        slot = blockedTimeSlotRepository.save(slot);
        return mapToBlockedTimeSlotDTO(slot);
    }
    
    public List<BlockedTimeSlotDTO> getBlockedSlots(LocalDateTime startDate, LocalDateTime endDate) {
        return blockedTimeSlotRepository.findByDateRange(startDate, endDate).stream()
            .map(this::mapToBlockedTimeSlotDTO)
            .collect(Collectors.toList());
    }
    
    @Transactional
    public void deleteBlockedSlot(Long id) {
        blockedTimeSlotRepository.deleteById(id);
    }
    
    // Available Slots Calculation
    public List<AvailableSlotDTO> getAvailableSlots(LocalDate date) {
        List<AvailableSlotDTO> slots = new ArrayList<>();
        
        // Get business hours for this day
        BusinessHoursDTO businessHours = getBusinessHoursByDay(date.getDayOfWeek());
        if (!businessHours.getIsOpen()) {
            return slots;
        }
        
        // Get settings from database
        AppointmentSettings settings = settingsRepository.findFirstByOrderByIdDesc()
            .orElseGet(() -> {
                AppointmentSettings defaultSettings = new AppointmentSettings();
                defaultSettings.setSlotDurationMinutes(60);
                defaultSettings.setMaxAppointmentsPerSlot(1);
                defaultSettings.setAdvanceBookingDays(60);
                defaultSettings.setBufferTimeBetweenAppointments(0);
                defaultSettings.setRequireApproval(true);
                defaultSettings.setAllowSameDayBooking(true);
                return settingsRepository.save(defaultSettings);
            });
        
        // Generate time slots using slotDurationMinutes
        LocalTime currentTime = businessHours.getOpenTime();
        LocalTime closeTime = businessHours.getCloseTime();
        
        // Handle business hours that span across midnight
        boolean spansMidnight = closeTime.isBefore(currentTime);
        LocalDate endDate = spansMidnight ? date.plusDays(1) : date;
        
        LocalDateTime slotStart = LocalDateTime.of(date, currentTime);
        LocalDateTime slotEnd = LocalDateTime.of(endDate, closeTime);
        
        while (slotStart.isBefore(slotEnd)) {
            LocalDateTime currentSlotEnd = slotStart.plusMinutes(settings.getSlotDurationMinutes());
            
            AvailableSlotDTO slot = checkSlotAvailability(slotStart, currentSlotEnd, settings);
            slots.add(slot);
            
            slotStart = currentSlotEnd;
        }
        
        return slots;
    }
    
    private AvailableSlotDTO checkSlotAvailability(LocalDateTime start, LocalDateTime end, AppointmentSettings settings) {
        AvailableSlotDTO slot = new AvailableSlotDTO();
        slot.setStartTime(start);
        slot.setEndTime(end);
        
        // Check if in the past
        if (start.isBefore(LocalDateTime.now())) {
            slot.setIsAvailable(false);
            slot.setAvailableSpots(0);
            slot.setReason("Past time");
            return slot;
        }
        
        // Check if blocked
        List<BlockedTimeSlot> blockedSlots = blockedTimeSlotRepository.findOverlappingSlots(start, end);
        if (!blockedSlots.isEmpty()) {
            slot.setIsAvailable(false);
            slot.setAvailableSpots(0);
            slot.setReason(blockedSlots.get(0).getReason());
            return slot;
        }
        
        // Check existing appointments
        long appointmentCount = appointmentRepository.countByAppointmentDateTimeBetween(start, end);
        int availableSpots = settings.getMaxAppointmentsPerSlot() - (int) appointmentCount;
        
        slot.setIsAvailable(availableSpots > 0);
        slot.setAvailableSpots(Math.max(0, availableSpots));
        if (availableSpots <= 0) {
            slot.setReason("Fully booked");
        }
        
        return slot;
    }
    
    // Helper methods
    private BusinessHoursDTO mapToBusinessHoursDTO(BusinessHours hours) {
        BusinessHoursDTO dto = new BusinessHoursDTO();
        dto.setId(hours.getId());
        dto.setDayOfWeek(hours.getDayOfWeek());
        dto.setOpenTime(hours.getOpenTime());
        dto.setCloseTime(hours.getCloseTime());
        dto.setIsOpen(hours.getIsOpen());
        dto.setNotes(hours.getNotes());
        return dto;
    }
    
    private BlockedTimeSlotDTO mapToBlockedTimeSlotDTO(BlockedTimeSlot slot) {
        BlockedTimeSlotDTO dto = new BlockedTimeSlotDTO();
        dto.setId(slot.getId());
        dto.setStartDateTime(slot.getStartDateTime());
        dto.setEndDateTime(slot.getEndDateTime());
        dto.setReason(slot.getReason());
        dto.setIsRecurring(slot.getIsRecurring());
        dto.setRecurrencePattern(slot.getRecurrencePattern());
        dto.setCreatedByName(slot.getCreatedBy() != null ? 
            slot.getCreatedBy().getFirstName() + " " + slot.getCreatedBy().getLastName() : null);
        dto.setCreatedAt(slot.getCreatedAt());
        return dto;
    }
    
    private BusinessHoursDTO createDefaultBusinessHours(DayOfWeek day) {
        BusinessHoursDTO dto = new BusinessHoursDTO();
        dto.setDayOfWeek(day);
        dto.setOpenTime(LocalTime.of(9, 0));
        dto.setCloseTime(LocalTime.of(17, 0));
        dto.setIsOpen(day != DayOfWeek.SUNDAY);
        return dto;
    }
}
