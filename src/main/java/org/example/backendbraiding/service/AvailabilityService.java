package org.example.backendbraiding.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.backendbraiding.dto.AvailableSlotDTO;
import org.example.backendbraiding.dto.BlockedTimeSlotDTO;
import org.example.backendbraiding.dto.BusinessHoursDTO;
import org.example.backendbraiding.dto.DayScheduleDTO;
import org.example.backendbraiding.dto.TimeSlotDTO;
import org.example.backendbraiding.dto.WeeklyScheduleDTO;
import org.example.backendbraiding.model.*;
import org.example.backendbraiding.repository.*;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AvailabilityService {

    private final BusinessHoursRepository businessHoursRepository;
    private final BlockedTimeSlotRepository blockedTimeSlotRepository;
    private final AppointmentSettingsRepository settingsRepository;
    private final AppointmentRepository appointmentRepository;
    private final AdminRepository adminRepository;
    private final TimeSlotRepository timeSlotRepository;
    
    @PostConstruct
    public void initializeDefaultSettings() {
        if (settingsRepository.count() == 0) {
            AppointmentSettings defaultSettings = new AppointmentSettings();
            defaultSettings.setSlotDurationMinutes(60);
            defaultSettings.setMaxAppointmentsPerSlot(1);
            defaultSettings.setAdvanceBookingDays(60);
            defaultSettings.setBufferTimeBetweenAppointments(0);
            defaultSettings.setRequireApproval(true);
            defaultSettings.setAllowSameDayBooking(true);
            defaultSettings.setTimezone("America/Los_Angeles");
            settingsRepository.save(defaultSettings);
        }
    }
    
    // Business Hours Management
    @Transactional
    @CacheEvict(value = "availableSlots", allEntries = true)
    public BusinessHoursDTO saveBusinessHours(BusinessHoursDTO dto) {
        // Validate business hours
        if (dto.getIsOpen() && dto.getCloseTime().isBefore(dto.getOpenTime())) {
            throw new IllegalArgumentException("Close time must be after open time");
        }

        BusinessHours hours = businessHoursRepository.findByDayOfWeek(dto.getDayOfWeek())
            .orElse(new BusinessHours());

        hours.setDayOfWeek(dto.getDayOfWeek());
        hours.setOpenTime(dto.getOpenTime());
        hours.setCloseTime(dto.getCloseTime());
        hours.setIsOpen(dto.getIsOpen());
        hours.setNotes(dto.getNotes());

        hours = businessHoursRepository.save(hours);
        
        // If day is being closed, delete associated time slots
        if (!dto.getIsOpen()) {
            timeSlotRepository.deleteByDayOfWeek(dto.getDayOfWeek().name());
        }
        
        return mapToBusinessHoursDTO(hours);
    }

    // Bulk Schedule Management
    @Transactional
    @CacheEvict(value = "availableSlots", allEntries = true)
    public void saveWeeklySchedule(WeeklyScheduleDTO dto) {
        if (dto == null || dto.getDays() == null) {
            return;
        }

        log.info("Saving weekly schedule with {} days", dto.getDays().size());

        // Collect all operations to batch them for better performance
        List<BusinessHours> businessHoursToSave = new ArrayList<>();
        List<String> daysToDeleteSlots = new ArrayList<>();
        List<TimeSlot> timeSlotsToSave = new ArrayList<>();

        dto.getDays().forEach(day -> {
            try {
                String dayKey = day.getDayOfWeek();
                DayOfWeek dayOfWeek = DayOfWeek.valueOf(dayKey);

                List<TimeSlotDTO> slots = day.getTimeSlots() != null
                        ? day.getTimeSlots()
                        : List.of();

                boolean isOpen = Boolean.TRUE.equals(day.getIsAvailable()) && !slots.isEmpty();

                log.debug("Processing day {}: isOpen={}, slots={}", dayOfWeek, isOpen, slots.size());

                BusinessHours hours = businessHoursRepository.findByDayOfWeek(dayOfWeek)
                        .orElse(new BusinessHours());

                hours.setDayOfWeek(dayOfWeek);
                hours.setIsOpen(isOpen);

                if (!isOpen) {
                    hours.setOpenTime(LocalTime.of(0, 0));
                    hours.setCloseTime(LocalTime.of(0, 0));
                    businessHoursToSave.add(hours);
                    daysToDeleteSlots.add(dayKey);
                    log.debug("Day {} is closed, will delete time slots", dayOfWeek);
                    return;
                }

                LocalTime openTime = LocalTime.parse(slots.get(0).getStartTime());
                LocalTime closeTime = LocalTime.parse(slots.get(slots.size() - 1).getEndTime());

                hours.setOpenTime(openTime);
                hours.setCloseTime(closeTime);
                businessHoursToSave.add(hours);
                daysToDeleteSlots.add(dayKey);

                for (int i = 0; i < slots.size(); i++) {
                    TimeSlotDTO slotDto = slots.get(i);

                    TimeSlot slot = new TimeSlot();
                    slot.setDayOfWeek(dayKey);
                    slot.setStartTime(LocalTime.parse(slotDto.getStartTime()));
                    slot.setEndTime(LocalTime.parse(slotDto.getEndTime()));
                    slot.setCapacity(slotDto.getCapacity() != null ? slotDto.getCapacity() : 1);
                    slot.setSlotOrder(i);

                    timeSlotsToSave.add(slot);
                }

                log.debug("Collected {} time slots for day {}", slots.size(), dayOfWeek);
            } catch (Exception e) {
                log.error("Error processing day {}: {}", day.getDayOfWeek(), e.getMessage(), e);
                throw new RuntimeException("Failed to process day " + day.getDayOfWeek() + ": " + e.getMessage(), e);
            }
        });

        // Batch delete all time slots
        if (!daysToDeleteSlots.isEmpty()) {
            log.debug("Deleting time slots for {} days", daysToDeleteSlots.size());
            daysToDeleteSlots.forEach(timeSlotRepository::deleteByDayOfWeek);
        }

        // Batch save all business hours
        if (!businessHoursToSave.isEmpty()) {
            log.debug("Saving {} business hours", businessHoursToSave.size());
            businessHoursRepository.saveAll(businessHoursToSave);
        }

        // Batch save all time slots
        if (!timeSlotsToSave.isEmpty()) {
            log.debug("Saving {} time slots", timeSlotsToSave.size());
            timeSlotRepository.saveAll(timeSlotsToSave);
        }

        log.info("Successfully saved weekly schedule");
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
    @CacheEvict(value = "availableSlots", allEntries = true)
    public BlockedTimeSlotDTO createBlockedSlot(BlockedTimeSlotDTO dto, String adminEmail) {
        Admin admin = adminRepository.findByEmail(adminEmail)
            .orElseThrow(() -> new RuntimeException("Admin not found"));
        
        // Validate recurrence pattern
        if (dto.getIsRecurring() && dto.getRecurrencePattern() != null) {
            String pattern = dto.getRecurrencePattern().toUpperCase();
            if (!pattern.equals("DAILY") && !pattern.equals("WEEKLY") && !pattern.equals("MONTHLY")) {
                throw new IllegalArgumentException("Invalid recurrence pattern. Must be DAILY, WEEKLY, or MONTHLY");
            }
        }
        
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
    @CacheEvict(value = "availableSlots", allEntries = true)
    public void deleteBlockedSlot(Long id) {
        blockedTimeSlotRepository.deleteById(id);
    }
    
    // Available Slots Calculation
    @Cacheable(value = "availableSlots", key = "#date + '-' + #timezone")
    public List<AvailableSlotDTO> getAvailableSlots(LocalDate date, String timezone) {
        List<AvailableSlotDTO> slots = new ArrayList<>();
        
        // Get business hours for this day
        BusinessHoursDTO businessHours = getBusinessHoursByDay(date.getDayOfWeek());
        if (!businessHours.getIsOpen()) {
            return slots;
        }
        
        // Get settings from database, create default if not exists
        AppointmentSettings settings = settingsRepository.findFirstByOrderByIdDesc()
            .orElseGet(() -> {
                AppointmentSettings defaultSettings = new AppointmentSettings();
                defaultSettings.setSlotDurationMinutes(60);
                defaultSettings.setMaxAppointmentsPerSlot(1);
                defaultSettings.setAdvanceBookingDays(60);
                defaultSettings.setBufferTimeBetweenAppointments(0);
                defaultSettings.setRequireApproval(true);
                defaultSettings.setAllowSameDayBooking(true);
                defaultSettings.setTimezone("America/Los_Angeles");
                return settingsRepository.save(defaultSettings);
            });
        
        // Use provided timezone or fall back to configured timezone
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(timezone);
        } catch (Exception e) {
            // If invalid timezone provided, use the configured one
            zoneId = ZoneId.of(settings.getTimezone());
        }
        
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
            
            AvailableSlotDTO slot = checkSlotAvailability(slotStart, currentSlotEnd, settings, zoneId);
            slots.add(slot);
            
            // Add buffer time between slots
            slotStart = currentSlotEnd.plusMinutes(settings.getBufferTimeBetweenAppointments());
        }
        
        return slots;
    }
    
    private AvailableSlotDTO checkSlotAvailability(LocalDateTime start, LocalDateTime end, AppointmentSettings settings, ZoneId timezone) {
        AvailableSlotDTO slot = new AvailableSlotDTO();
        slot.setStartTime(start);
        slot.setEndTime(end);
        
        // Check if in the past (using configured timezone)
        ZonedDateTime now = ZonedDateTime.now(timezone);
        ZonedDateTime slotStartZoned = start.atZone(timezone);
        
        if (slotStartZoned.isBefore(now)) {
            slot.setIsAvailable(false);
            slot.setAvailableSpots(0);
            slot.setReason("Past time");
            return slot;
        }
        
        // Check if blocked (including recurring blocks)
        List<BlockedTimeSlot> blockedSlots = blockedTimeSlotRepository.findOverlappingSlots(start, end);
        
        // Check for recurring blocks that might apply to this slot (cached for performance)
        List<BlockedTimeSlot> allRecurringBlocks = getAllRecurringBlocks();
        for (BlockedTimeSlot recurringBlock : allRecurringBlocks) {
            if (isRecurringBlockActive(recurringBlock, start, end, timezone)) {
                blockedSlots.add(recurringBlock);
            }
        }
        
        if (!blockedSlots.isEmpty()) {
            slot.setIsAvailable(false);
            slot.setAvailableSpots(0);
            slot.setReason(blockedSlots.get(0).getReason());
            return slot;
        }
        
        // Check existing appointments (true interval overlap, excluding expired unpaid reservations)
        LocalDateTime windowStart = start.minusMinutes(settings.getSlotDurationMinutes());
        long appointmentCount = appointmentRepository.countOverlapping(windowStart, end, LocalDateTime.now());
        int availableSpots = settings.getMaxAppointmentsPerSlot() - (int) appointmentCount;
        
        slot.setIsAvailable(availableSpots > 0);
        slot.setAvailableSpots(Math.max(0, availableSpots));
        if (availableSpots <= 0) {
            slot.setReason("Fully booked");
        }
        
        return slot;
    }
    
    public List<BlockedTimeSlot> getAllRecurringBlocks() {
        return blockedTimeSlotRepository.findByIsRecurringTrue();
    }
    
    private boolean isRecurringBlockActive(BlockedTimeSlot recurringBlock, LocalDateTime slotStart, LocalDateTime slotEnd, ZoneId timezone) {
        if (!recurringBlock.getIsRecurring() || recurringBlock.getRecurrencePattern() == null) {
            return false;
        }
        
        LocalDateTime blockStart = recurringBlock.getStartDateTime();
        LocalDateTime blockEnd = recurringBlock.getEndDateTime();
        
        // Simple daily recurrence check
        if (recurringBlock.getRecurrencePattern().equalsIgnoreCase("DAILY")) {
            // Check if the slot time matches the block time on any day
            LocalTime blockStartTime = blockStart.toLocalTime();
            LocalTime blockEndTime = blockEnd.toLocalTime();
            LocalTime slotStartTime = slotStart.toLocalTime();
            LocalTime slotEndTime = slotEnd.toLocalTime();
            
            // Check if times overlap
            return !(slotEndTime.isBefore(blockStartTime) || slotStartTime.isAfter(blockEndTime));
        }
        
        // Weekly recurrence check
        if (recurringBlock.getRecurrencePattern().startsWith("WEEKLY")) {
            // Check if it's the same day of week
            if (blockStart.getDayOfWeek() != slotStart.getDayOfWeek()) {
                return false;
            }
            
            LocalTime blockStartTime = blockStart.toLocalTime();
            LocalTime blockEndTime = blockEnd.toLocalTime();
            LocalTime slotStartTime = slotStart.toLocalTime();
            LocalTime slotEndTime = slotEnd.toLocalTime();
            
            return !(slotEndTime.isBefore(blockStartTime) || slotStartTime.isAfter(blockEndTime));
        }
        
        return false;
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
