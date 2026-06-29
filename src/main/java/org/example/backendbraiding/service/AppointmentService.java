package org.example.backendbraiding.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.backendbraiding.dto.AppointmentActionDTO;
import org.example.backendbraiding.dto.AppointmentRequestDTO;
import org.example.backendbraiding.dto.AppointmentResponseDTO;
import org.example.backendbraiding.dto.AppointmentSettingsDTO;
import org.example.backendbraiding.model.*;
import org.example.backendbraiding.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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

    @Transactional
    public AppointmentResponseDTO createAppointment(AppointmentRequestDTO requestDTO) {
        AppointmentSettings settings = settingsRepository.findFirstByOrderByIdDesc()
            .orElseGet(this::createDefaultSettings);
        
        validateAppointmentDateTime(requestDTO.getAppointmentDateTime(), settings);
        validateAppointmentAvailability(requestDTO.getAppointmentDateTime(), settings);
        
        Customer customer = customerRepository.findByEmail(requestDTO.getEmail())
            .orElseGet(() -> {
                Customer newCustomer = new Customer();
                newCustomer.setEmail(requestDTO.getEmail());
                return newCustomer;
            });
        
        customer.setFirstName(requestDTO.getFirstName());
        customer.setLastName(requestDTO.getLastName());
        customer.setPhoneNumber(requestDTO.getPhoneNumber());
        customer = customerRepository.save(customer);

        Appointment appointment = new Appointment();
        appointment.setCustomer(customer);
        appointment.setAppointmentDateTime(requestDTO.getAppointmentDateTime());
        appointment.setNotes(requestDTO.getNotes());
        appointment.setSelectedService(requestDTO.getSelectedService() != null ? requestDTO.getSelectedService() : requestDTO.getServiceName());
        appointment.setSelectedSize(requestDTO.getSelectedSize());
        appointment.setSelectedLength(requestDTO.getSelectedLength());
        appointment.setPrice(requestDTO.getPrice());
        appointment.setStatus(Appointment.AppointmentStatus.PENDING);

        if (requestDTO.getServiceId() != null) {
            ServiceItem service = serviceItemRepository.findById(requestDTO.getServiceId())
                .orElseThrow(() -> new RuntimeException("Service not found"));
            appointment.setService(service);
        } else if (requestDTO.getServiceName() != null && !requestDTO.getServiceName().isEmpty()) {
            log.debug("Looking for service with name: {}", requestDTO.getServiceName());
            ServiceItem service = serviceItemRepository.findFirstByNameContainingIgnoreCase(requestDTO.getServiceName());
            if (service != null) {
                log.debug("Found service: {}", service.getName());
                appointment.setService(service);
            } else {
                log.warn("Service not found for name: {}", requestDTO.getServiceName());
            }
        }

        Appointment savedAppointment = appointmentRepository.save(appointment);
        return mapToResponseDTO(savedAppointment);
    }

    @Transactional
    public AppointmentResponseDTO approveAppointment(Long appointmentId, Long adminId, AppointmentActionDTO actionDTO) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (appointment.getStatus() != Appointment.AppointmentStatus.PENDING) {
            throw new RuntimeException("Only pending appointments can be approved");
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
            try {
                paymentService.capturePayment(new org.example.backendbraiding.dto.PaymentCaptureRequest(
                    appointment.getPaymentIntentId(), null));
            } catch (Exception e) {
                throw new RuntimeException("Failed to capture payment: " + e.getMessage());
            }
        }
        
        String customerName = appointment.getCustomer().getFirstName();
        String appointmentTime = appointment.getAppointmentDateTime().toString();
        smsService.sendAppointmentApprovedSms(
            appointment.getCustomer().getPhoneNumber(),
            customerName,
            appointmentTime
        );
        
        return mapToResponseDTO(updatedAppointment);
    }

    @Transactional
    public AppointmentResponseDTO denyAppointment(Long appointmentId, Long adminId, AppointmentActionDTO actionDTO) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (appointment.getStatus() != Appointment.AppointmentStatus.PENDING) {
            throw new RuntimeException("Only pending appointments can be denied");
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
        
        if (appointment.getPaymentIntentId() != null && 
            appointment.getPaymentStatus() == Appointment.PaymentStatus.AUTHORIZED) {
            try {
                paymentService.cancelPayment(appointment.getPaymentIntentId());
            } catch (Exception e) {
                throw new RuntimeException("Failed to cancel payment: " + e.getMessage());
            }
        }
        
        String customerName = appointment.getCustomer().getFirstName();
        smsService.sendAppointmentDeniedSms(
            appointment.getCustomer().getPhoneNumber(),
            customerName,
            actionDTO.getAdminNotes()
        );
        
        return mapToResponseDTO(updatedAppointment);
    }

    public List<AppointmentResponseDTO> getPendingAppointments() {
        return appointmentRepository.findByStatus(Appointment.AppointmentStatus.PENDING)
            .stream()
            .map(this::mapToResponseDTO)
            .collect(Collectors.toList());
    }

    public List<AppointmentResponseDTO> getAllAppointments() {
        return appointmentRepository.findAll()
            .stream()
            .map(this::mapToResponseDTO)
            .collect(Collectors.toList());
    }

    public List<AppointmentResponseDTO> getUpcomingAppointments() {
        return appointmentRepository.findUpcomingAppointments(LocalDateTime.now())
            .stream()
            .map(this::mapToResponseDTO)
            .collect(Collectors.toList());
    }

    public List<AppointmentResponseDTO> getAppointmentsByStatus(String status) {
        try {
            Appointment.AppointmentStatus appointmentStatus = Appointment.AppointmentStatus.valueOf(status.toUpperCase());
            return appointmentRepository.findByStatus(appointmentStatus)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid appointment status: " + status + ". Valid values are: PENDING, APPROVED, DENIED, CANCELLED, COMPLETED");
        }
    }

    public AppointmentResponseDTO getAppointmentById(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Appointment not found"));
        return mapToResponseDTO(appointment);
    }

    public List<AppointmentResponseDTO> getAppointmentsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return appointmentRepository.findAppointmentsBetweenDates(startDate, endDate)
            .stream()
            .map(this::mapToResponseDTO)
            .collect(Collectors.toList());
    }

    private AppointmentResponseDTO mapToResponseDTO(Appointment appointment) {
        AppointmentResponseDTO dto = new AppointmentResponseDTO();
        dto.setId(appointment.getId());
        dto.setAppointmentDateTime(appointment.getAppointmentDateTime());
        dto.setStatus(appointment.getStatus().name());
        dto.setNotes(appointment.getNotes());
        dto.setAdminNotes(appointment.getAdminNotes());
        dto.setSelectedService(appointment.getSelectedService());
        dto.setSelectedSize(appointment.getSelectedSize());
        dto.setSelectedLength(appointment.getSelectedLength());
        dto.setPrice(appointment.getPrice());
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
        return settingsRepository.save(defaultSettings);
    }
    
    private void validateAppointmentDateTime(LocalDateTime appointmentDateTime, AppointmentSettings settings) {
        LocalDateTime now = LocalDateTime.now();
        
        if (!settings.getAllowSameDayBooking() && appointmentDateTime.toLocalDate().equals(now.toLocalDate())) {
            throw new RuntimeException("Same-day booking is not allowed");
        }
        
        LocalDateTime maxBookingDate = now.plusDays(settings.getAdvanceBookingDays());
        if (appointmentDateTime.isAfter(maxBookingDate)) {
            throw new RuntimeException("Appointment cannot be booked more than " + 
                settings.getAdvanceBookingDays() + " days in advance");
        }
        
        if (appointmentDateTime.isBefore(now)) {
            throw new RuntimeException("Appointment cannot be in the past");
        }
    }
    
    private void validateAppointmentAvailability(LocalDateTime appointmentDateTime, AppointmentSettings settings) {
        BusinessHours businessHours = businessHoursRepository.findByDayOfWeek(appointmentDateTime.getDayOfWeek())
            .orElse(null);
        
        if (businessHours == null || !businessHours.getIsOpen()) {
            throw new RuntimeException("Business is closed on " + appointmentDateTime.getDayOfWeek());
        }
        
        if (appointmentDateTime.toLocalTime().isBefore(businessHours.getOpenTime()) || 
            appointmentDateTime.toLocalTime().isAfter(businessHours.getCloseTime())) {
            throw new RuntimeException("Appointment time is outside business hours (" + 
                businessHours.getOpenTime() + " - " + businessHours.getCloseTime() + ")");
        }
        
        LocalDateTime slotEnd = appointmentDateTime.plusMinutes(settings.getSlotDurationMinutes());
        List<BlockedTimeSlot> blockedSlots = blockedTimeSlotRepository.findOverlappingSlots(appointmentDateTime, slotEnd);
        if (!blockedSlots.isEmpty()) {
            throw new RuntimeException("This time slot is blocked: " + blockedSlots.get(0).getReason());
        }
        
        long appointmentCount = appointmentRepository.countByAppointmentDateTimeBetween(appointmentDateTime, slotEnd);
        if (appointmentCount >= settings.getMaxAppointmentsPerSlot()) {
            throw new RuntimeException("This time slot is fully booked");
        }
    }
}
