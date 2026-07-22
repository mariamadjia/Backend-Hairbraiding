package org.example.backendbraiding.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.backendbraiding.dto.AppointmentActionDTO;
import org.example.backendbraiding.dto.AppointmentRequestDTO;
import org.example.backendbraiding.dto.AppointmentResponseDTO;
import org.example.backendbraiding.dto.AppointmentSettingsDTO;
import org.example.backendbraiding.model.*;
import org.example.backendbraiding.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.example.backendbraiding.util.BookingRules;
import jakarta.persistence.EntityManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;
    private final SmsService smsService;
    private final ServiceItemRepository serviceItemRepository;
    private final AdminRepository adminRepository;
    private final AppointmentSettingsRepository settingsRepository;
    private final PaymentService paymentService;
    private final BusinessHoursRepository businessHoursRepository;
    private final BlockedTimeSlotRepository blockedTimeSlotRepository;
    private final BookingPaymentTokenService bookingPaymentTokenService;
    private final TimeSlotRepository timeSlotRepository;
    private final EmailService emailService;
    private final EntityManager entityManager;

    private static final int RESERVATION_TTL_MINUTES = 15;

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = {"appointments", "availableSlots"}, allEntries = true)
    public AppointmentResponseDTO createAppointment(AppointmentRequestDTO requestDTO) {
        AppointmentSettings settings = settingsRepository.findLatestForUpdate()
            .orElseGet(this::createDefaultSettings);

        ServiceItem service = serviceItemRepository.findById(requestDTO.getServiceId())
                .orElseThrow(() -> new org.example.backendbraiding.exception.ResourceNotFoundException("Service not found"));
        LengthOption lengthOption = resolveLengthOption(service, requestDTO.getLengthOptionId(), requestDTO.getSelectedLength());
        validateAppointmentDateTime(requestDTO.getAppointmentDateTime(), settings);
        validateAppointmentAvailability(requestDTO.getAppointmentDateTime(), settings);
        
        String normalizedEmail = requestDTO.getEmail().trim().toLowerCase(Locale.ROOT);
        // Serialize customer creation per normalized email across application instances.
        // This closes the race where two simultaneous first bookings create duplicate customers.
        entityManager.createNativeQuery("WITH customer_lock AS (" +
                        "SELECT pg_advisory_xact_lock(hashtextextended(?1, 0))" +
                        ") SELECT hashtextextended(?1, 0) FROM customer_lock")
                .setParameter(1, normalizedEmail)
                .getSingleResult();
        Customer customer = customerRepository.findFirstByEmailIgnoreCaseOrderByIdAsc(normalizedEmail)
            .orElseGet(() -> {
                Customer newCustomer = new Customer();
                newCustomer.setEmail(normalizedEmail);
                return newCustomer;
            });
        
        customer.setFirstName(requestDTO.getFirstName());
        customer.setLastName(requestDTO.getLastName());
        customer.setPhoneNumber(requestDTO.getPhoneNumber().trim());
        customer = customerRepository.save(customer);

        Appointment appointment = new Appointment();
        appointment.setCustomer(customer);
        appointment.setService(service);
        appointment.setAppointmentDateTime(requestDTO.getAppointmentDateTime());
        appointment.setAppointmentEndDateTime(null);
        appointment.setNotes(requestDTO.getNotes());
        appointment.setSelectedService(service.getName());
        appointment.setSelectedSize(requestDTO.getSelectedSize());
        appointment.setSelectedLength(lengthOption != null ? lengthOption.getName() : requestDTO.getSelectedLength());
        appointment.setPrice(lengthOption != null && lengthOption.getPrice() != null ? lengthOption.getPrice() : service.getPrice());
        appointment.setDurationMinutes(null);
        appointment.setStatus(Appointment.AppointmentStatus.PENDING);
        appointment.setPaymentPendingExpiresAt(LocalDateTime.now().plusMinutes(RESERVATION_TTL_MINUTES));

        Appointment savedAppointment = appointmentRepository.save(appointment);
        AppointmentResponseDTO response = mapToResponseDTO(savedAppointment);
        response.setPaymentToken(bookingPaymentTokenService.createToken(savedAppointment.getId()));
        emailService.sendAppointmentUpdate(customer.getEmail(), "Appointment request received",
                "We received your appointment request for " + appointment.getAppointmentDateTime()
                        + " Central Time. Complete the payment authorization to send it for review.");
        return response;
    }

    /**
     * Releases abandoned reservations: appointments left in PENDING/PENDING payment status
     * past their reservation expiry are cancelled and any Stripe authorization is released.
     */
    @Scheduled(fixedRate = 60000)
    @org.springframework.cache.annotation.CacheEvict(value = {"appointments", "availableSlots"}, allEntries = true)
    @Transactional
    public void releaseExpiredReservations() {
        List<Appointment> expired = appointmentRepository.findExpiredPendingReservations(LocalDateTime.now());
        expired.addAll(appointmentRepository.findFailedPendingReservations());
        for (Appointment appointment : expired) {
            log.info("Releasing expired reservation for appointment {}", appointment.getId());
            if (appointment.getPaymentIntentId() != null) {
                try {
                    paymentService.cancelPayment(appointment.getPaymentIntentId());
                } catch (Exception e) {
                    log.warn("Could not cancel Stripe payment intent {} for expired appointment {}: {}",
                        appointment.getPaymentIntentId(), appointment.getId(), e.getMessage());
                }
            }
            appointment.setStatus(Appointment.AppointmentStatus.CANCELLED);
            appointment.setPaymentStatus(Appointment.PaymentStatus.CANCELLED);
            appointment.setAdminNotes("Automatically cancelled: payment was not completed in time");
            appointmentRepository.save(appointment);
        }
    }

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = "appointments", allEntries = true)
    public AppointmentResponseDTO approveAppointment(Long appointmentId, Long adminId, AppointmentActionDTO actionDTO) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (appointment.getStatus() != Appointment.AppointmentStatus.PENDING) {
            throw new IllegalStateException("Only pending appointments can be approved");
        }
        if (!appointment.getAppointmentDateTime().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("Past appointments cannot be approved");
        }
        if (appointment.getPaymentStatus() != Appointment.PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException("Payment must be authorized before approving an appointment");
        }

        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new RuntimeException("Admin not found"));

        appointment.setStatus(Appointment.AppointmentStatus.APPROVED);
        appointment.setApprovedBy(admin);
        appointment.setApprovedAt(LocalDateTime.now());
        
        if (actionDTO.getAdminNotes() != null) {
            appointment.setAdminNotes(actionDTO.getAdminNotes());
        }

        Appointment updatedAppointment = appointmentRepository.save(appointment);

        if (appointment.getPaymentIntentId() != null &&
            appointment.getPaymentStatus() == Appointment.PaymentStatus.AUTHORIZED) {
            String paymentIntentId = appointment.getPaymentIntentId();
            // Capture only after the approval commits, so a failed capture never leaves
            // the appointment approved without a corresponding charge decision.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        paymentService.capturePayment(new org.example.backendbraiding.dto.PaymentCaptureRequest(
                            paymentIntentId, null));
                    } catch (Exception e) {
                        paymentService.markCaptureFailed(paymentIntentId, e.getMessage());
                        log.error("Failed to capture payment {} after approval commit: {}", paymentIntentId, e.getMessage(), e);
                    }
                }
            });
        }
        
        String customerName = appointment.getCustomer().getFirstName();
        String appointmentTime = appointment.getAppointmentDateTime().toString();
        smsService.sendAppointmentApprovedSms(
            appointment.getCustomer().getPhoneNumber(),
            customerName,
            appointmentTime
        );
        emailService.sendAppointmentUpdate(appointment.getCustomer().getEmail(), "Appointment approved",
                "Your appointment for " + appointment.getAppointmentDateTime() + " Central Time has been approved.");
        
        return mapToResponseDTO(updatedAppointment);
    }

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = "appointments", allEntries = true)
    public AppointmentResponseDTO denyAppointment(Long appointmentId, Long adminId, AppointmentActionDTO actionDTO) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (appointment.getStatus() != Appointment.AppointmentStatus.PENDING) {
            throw new IllegalStateException("Only pending appointments can be denied");
        }
        if (actionDTO.getAdminNotes() == null || actionDTO.getAdminNotes().isBlank()) {
            throw new IllegalArgumentException("A denial reason is required");
        }

        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new RuntimeException("Admin not found"));

        appointment.setStatus(Appointment.AppointmentStatus.DENIED);
        appointment.setApprovedBy(admin);
        appointment.setApprovedAt(LocalDateTime.now());
        
        if (actionDTO.getAdminNotes() != null) {
            appointment.setAdminNotes(actionDTO.getAdminNotes());
        }

        Appointment updatedAppointment = appointmentRepository.save(appointment);
        
        if (appointment.getPaymentIntentId() != null && appointment.getPaymentStatus() == Appointment.PaymentStatus.AUTHORIZED) {
            String paymentIntentId = appointment.getPaymentIntentId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        paymentService.cancelPayment(paymentIntentId);
                    } catch (Exception e) {
                        paymentService.markCancellationFailed(paymentIntentId, e.getMessage());
                    }
                }
            });
        }
        
        String customerName = appointment.getCustomer().getFirstName();
        smsService.sendAppointmentDeniedSms(
            appointment.getCustomer().getPhoneNumber(),
            customerName,
            actionDTO.getAdminNotes()
        );
        emailService.sendAppointmentUpdate(appointment.getCustomer().getEmail(), "Appointment request update",
                "Your appointment request could not be approved. "
                        + (actionDTO.getAdminNotes() == null ? "Please contact the salon." : actionDTO.getAdminNotes()));
        
        return mapToResponseDTO(updatedAppointment);
    }

    public Page<AppointmentResponseDTO> getPendingAppointments(Pageable pageable) {
        return appointmentRepository.findByStatus(Appointment.AppointmentStatus.PENDING, pageable)
            .map(this::mapToResponseDTO);
    }

    public Page<AppointmentResponseDTO> getAllAppointments(Pageable pageable) {
        return appointmentRepository.findAll(pageable)
            .map(this::mapToResponseDTO);
    }

    public List<AppointmentResponseDTO> getUpcomingAppointments() {
        AppointmentSettings settings = settingsRepository.findFirstByOrderByIdDesc()
                .orElseGet(this::createDefaultSettings);
        LocalDateTime salonNow = ZonedDateTime.now(salonZone(settings)).toLocalDateTime();
        return appointmentRepository.findUpcomingAppointments(salonNow)
            .stream()
            .map(this::mapToResponseDTO)
            .collect(Collectors.toList());
    }

    public Page<AppointmentResponseDTO> getAppointmentsByStatus(String status, Pageable pageable) {
        try {
            Appointment.AppointmentStatus appointmentStatus = Appointment.AppointmentStatus.valueOf(status.toUpperCase());
            return appointmentRepository.findByStatus(appointmentStatus, pageable)
                .map(this::mapToResponseDTO);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid appointment status: " + status + ". Valid values are: PENDING, APPROVED, DENIED, CANCELLED, COMPLETED");
        }
    }

    public AppointmentResponseDTO getAppointmentById(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Appointment not found"));
        return mapToResponseDTO(appointment);
    }

    public List<AppointmentResponseDTO> getAppointmentsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return appointmentRepository.findAppointmentsBetweenDates(startDate, endDate, Pageable.unpaged())
            .stream()
            .map(this::mapToResponseDTO)
            .collect(Collectors.toList());
    }

    private AppointmentResponseDTO mapToResponseDTO(Appointment appointment) {
        AppointmentResponseDTO dto = new AppointmentResponseDTO();
        dto.setId(appointment.getId());
        dto.setAppointmentDateTime(appointment.getAppointmentDateTime());
        dto.setAppointmentEndDateTime(appointment.getAppointmentEndDateTime());
        dto.setStatus(appointment.getStatus().name());
        dto.setNotes(appointment.getNotes());
        dto.setAdminNotes(appointment.getAdminNotes());
        dto.setSelectedService(appointment.getSelectedService());
        dto.setSelectedSize(appointment.getSelectedSize());
        dto.setSelectedLength(appointment.getSelectedLength());
        dto.setPrice(appointment.getPrice());
        dto.setDurationMinutes(appointment.getDurationMinutes());
        dto.setApprovedAt(appointment.getApprovedAt());
        dto.setCreatedAt(appointment.getCreatedAt());
        dto.setUpdatedAt(appointment.getUpdatedAt());

        AppointmentResponseDTO.CustomerDTO customerDTO = new AppointmentResponseDTO.CustomerDTO();
        customerDTO.setId(appointment.getCustomer().getId());
        customerDTO.setFirstName(appointment.getCustomer().getFirstName());
        customerDTO.setLastName(appointment.getCustomer().getLastName());
        customerDTO.setEmail(appointment.getCustomer().getEmail());
        customerDTO.setPhoneNumber(appointment.getCustomer().getPhoneNumber());
        dto.setCustomer(customerDTO);

        if (appointment.getService() != null) {
            AppointmentResponseDTO.ServiceDTO serviceDTO = new AppointmentResponseDTO.ServiceDTO();
            serviceDTO.setId(appointment.getService().getId());
            serviceDTO.setName(appointment.getService().getName());
            serviceDTO.setDescription(appointment.getService().getDescription());
            dto.setService(serviceDTO);
        }

        if (appointment.getApprovedBy() != null) {
            dto.setApprovedByName(appointment.getApprovedBy().getFirstName() + " " + 
                                 appointment.getApprovedBy().getLastName());
        }
        
        dto.setPaymentIntentId(appointment.getPaymentIntentId());
        if (appointment.getPaymentStatus() != null) {
            dto.setPaymentStatus(appointment.getPaymentStatus().name());
        }
        dto.setDepositAmount(appointment.getDepositAmount());
        dto.setPaymentCapturedAt(appointment.getPaymentCapturedAt());
        dto.setPaymentMethodLast4(appointment.getPaymentMethodLast4());
        dto.setPaymentMethodBrand(appointment.getPaymentMethodBrand());

        return dto;
    }

    public AppointmentSettingsDTO getSettings() {
        AppointmentSettings settings = settingsRepository.findFirstByOrderByIdDesc()
            .orElseGet(this::createDefaultSettings);
        
        return mapToSettingsDTO(settings);
    }

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = {"appointments", "availableSlots"}, allEntries = true)
    public AppointmentSettingsDTO updateSettings(AppointmentSettingsDTO dto, Long adminId) {
        AppointmentSettings settings = settingsRepository.findFirstByOrderByIdDesc()
            .orElseGet(this::createDefaultSettings);
        
        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new RuntimeException("Admin not found"));
        
        settings.setSlotDurationMinutes(dto.getSlotDurationMinutes());
        settings.setAdvanceBookingDays(dto.getAdvanceBookingDays());
        settings.setMaxAppointmentsPerSlot(dto.getMaxAppointmentsPerSlot());
        settings.setRequireApproval(dto.getRequireApproval());
        settings.setAllowSameDayBooking(dto.getAllowSameDayBooking());
        settings.setUpdatedAt(LocalDateTime.now());
        settings.setUpdatedBy(admin);
        
        settings = settingsRepository.save(settings);

        List<TimeSlot> existingSlots = timeSlotRepository.findAll();
        existingSlots.forEach(slot -> slot.setCapacity(dto.getMaxAppointmentsPerSlot()));
        timeSlotRepository.saveAll(existingSlots);
        
        return mapToSettingsDTO(settings);
    }

    private AppointmentSettingsDTO mapToSettingsDTO(AppointmentSettings settings) {
        AppointmentSettingsDTO dto = new AppointmentSettingsDTO();
        dto.setSlotDurationMinutes(settings.getSlotDurationMinutes());
        dto.setAdvanceBookingDays(settings.getAdvanceBookingDays());
        dto.setMaxAppointmentsPerSlot(settings.getMaxAppointmentsPerSlot());
        dto.setRequireApproval(settings.getRequireApproval());
        dto.setAllowSameDayBooking(settings.getAllowSameDayBooking());
        dto.setUpdatedAt(settings.getUpdatedAt());
        if (settings.getUpdatedBy() != null) {
            dto.setUpdatedByName(settings.getUpdatedBy().getFirstName() + " " + 
                               settings.getUpdatedBy().getLastName());
        }
        return dto;
    }
    
    private AppointmentSettings createDefaultSettings() {
        AppointmentSettings defaultSettings = new AppointmentSettings();
        defaultSettings.setSlotDurationMinutes(60);
        defaultSettings.setMaxAppointmentsPerSlot(1);
        defaultSettings.setAdvanceBookingDays(60);
        defaultSettings.setBufferTimeBetweenAppointments(0);
        defaultSettings.setRequireApproval(true);
        defaultSettings.setAllowSameDayBooking(true);
        defaultSettings.setTimezone("America/Chicago");
        return settingsRepository.save(defaultSettings);
    }
    
    private void validateAppointmentDateTime(LocalDateTime appointmentDateTime, AppointmentSettings settings) {
        ZoneId salonZone = salonZone(settings);
        LocalDateTime now = ZonedDateTime.now(salonZone).toLocalDateTime();
        
        if (!Boolean.TRUE.equals(settings.getAllowSameDayBooking()) && appointmentDateTime.toLocalDate().equals(now.toLocalDate())) {
            throw new IllegalArgumentException("Same-day booking is not allowed");
        }
        
        int advanceBookingDays = advanceBookingDays(settings);
        LocalDate maxBookingDate = now.toLocalDate().plusDays(advanceBookingDays);
        if (appointmentDateTime.toLocalDate().isAfter(maxBookingDate)) {
            throw new IllegalArgumentException("Appointment cannot be booked more than " +
                advanceBookingDays + " days in advance");
        }
        
        if (appointmentDateTime.isBefore(now)) {
            throw new IllegalArgumentException("Appointment cannot be in the past");
        }
    }
    
    private void validateAppointmentAvailability(LocalDateTime appointmentDateTime, AppointmentSettings settings) {
        BusinessHours businessHours = businessHoursRepository.findByDayOfWeek(appointmentDateTime.getDayOfWeek())
            .orElse(null);
        
        if (businessHours == null || !businessHours.getIsOpen()) {
            throw new IllegalArgumentException("Business is closed on " + appointmentDateTime.getDayOfWeek());
        }
        
        LocalDateTime businessOpen = LocalDateTime.of(appointmentDateTime.toLocalDate(), businessHours.getOpenTime());
        LocalDateTime businessClose = LocalDateTime.of(appointmentDateTime.toLocalDate(), businessHours.getCloseTime());
        if (!businessHours.getCloseTime().isAfter(businessHours.getOpenTime())) {
            businessClose = businessClose.plusDays(1);
        }
        if (appointmentDateTime.isBefore(businessOpen) || !appointmentDateTime.isBefore(businessClose)) {
            throw new IllegalArgumentException("Appointment time is outside business hours (" +
                businessHours.getOpenTime() + " - " + businessHours.getCloseTime() + ")");
        }

        List<TimeSlot> configuredSlots = timeSlotRepository.findByDayOfWeekOrderBySlotOrderAsc(
                appointmentDateTime.getDayOfWeek().name());
        if (configuredSlots.isEmpty()) {
            long minutesFromOpening = java.time.Duration.between(businessOpen, appointmentDateTime).toMinutes();
            if (minutesFromOpening % slotIntervalMinutes(settings) != 0) {
                throw new IllegalArgumentException("Appointment time must match an available slot");
            }
        }
        List<BlockedTimeSlot> blockedSlots = blockedTimeSlotRepository.findBlockingStart(appointmentDateTime);
        blockedTimeSlotRepository.findByIsRecurringTrue().stream()
            .filter(block -> BookingRules.recurringBlockContains(block, appointmentDateTime))
            .forEach(blockedSlots::add);
        if (!blockedSlots.isEmpty()) {
            throw new IllegalStateException("This time slot is blocked: " + blockedSlots.get(0).getReason());
        }
        
        int capacity = maximumCapacity(settings);
        if (!configuredSlots.isEmpty()) {
            TimeSlot configured = configuredSlots.stream()
                    .filter(slot -> {
                        LocalTime appointmentTime = appointmentDateTime.toLocalTime();
                        if (appointmentTime.isBefore(slot.getStartTime()) || !appointmentTime.isBefore(slot.getEndTime())) {
                            return false;
                        }
                        long minutesFromWindowStart = java.time.Duration.between(
                                slot.getStartTime(), appointmentTime).toMinutes();
                        return minutesFromWindowStart % slotIntervalMinutes(settings) == 0;
                    })
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Appointment time is not a configured slot"));
            capacity = configured.getCapacity() == null || configured.getCapacity() < 1
                    ? 1 : configured.getCapacity();
        }

        LocalDateTime salonNow = ZonedDateTime.now(salonZone(settings)).toLocalDateTime();
        long appointmentCount = appointmentRepository.countActiveAtStart(appointmentDateTime, salonNow);
        if (appointmentCount >= capacity) {
            throw new IllegalStateException("This time slot is fully booked");
        }
    }

    private ZoneId salonZone(AppointmentSettings settings) {
        try {
            String configured = settings.getTimezone();
            return configured == null || configured.isBlank()
                    ? ZoneId.of("America/Chicago")
                    : ZoneId.of(configured);
        } catch (Exception ignored) {
            return ZoneId.of("America/Chicago");
        }
    }

    private int slotIntervalMinutes(AppointmentSettings settings) {
        Integer configured = settings.getSlotDurationMinutes();
        return configured == null || configured < 1 ? 60 : configured;
    }

    private int advanceBookingDays(AppointmentSettings settings) {
        Integer configured = settings.getAdvanceBookingDays();
        return configured == null || configured < 0 ? 60 : configured;
    }

    private int maximumCapacity(AppointmentSettings settings) {
        Integer configured = settings.getMaxAppointmentsPerSlot();
        return configured == null || configured < 1 ? 1 : configured;
    }

    private LengthOption resolveLengthOption(ServiceItem service, Long optionId, String selectedLength) {
        if (optionId == null && (selectedLength == null || selectedLength.isBlank())) return null;
        return service.getLengthOptions().stream()
                .filter(option -> optionId != null ? optionId.equals(option.getId())
                        : option.getName() != null && option.getName().equalsIgnoreCase(selectedLength.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Selected length is not available for this service"));
    }

}
