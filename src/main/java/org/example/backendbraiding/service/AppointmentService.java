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
import org.springframework.data.jpa.domain.Specification;
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
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.math.BigDecimal;
import java.util.stream.Collectors;
import org.example.backendbraiding.util.BookingRules;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;

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
    private final BookingQuoteTokenService bookingQuoteTokenService;
    private final TimeSlotRepository timeSlotRepository;
    private final EmailService emailService;
    private final EntityManager entityManager;

    private static final int RESERVATION_TTL_MINUTES = 15;

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = {"appointments", "availableSlots"}, allEntries = true)
    public AppointmentResponseDTO createAppointment(AppointmentRequestDTO requestDTO) {
        AppointmentSettings settings = settingsRepository.findLatestForUpdate()
            .orElseGet(this::createDefaultSettings);

        ServiceItem service = serviceItemRepository.findByIdAndActiveTrue(requestDTO.getServiceId())
                .orElseThrow(() -> new org.example.backendbraiding.exception.ResourceNotFoundException("Service not found"));
        LengthOption lengthOption = resolveLengthOption(service, requestDTO.getLengthOptionId(), requestDTO.getSelectedLength());
        String foundation = resolveFoundation(service, requestDTO.getSelectedFoundation());
        BookingQuoteTokenService.QuoteClaims quote = bookingQuoteTokenService.parse(requestDTO.getQuoteToken());
        validateQuote(quote, service, lengthOption, foundation);
        validateAppointmentDateTime(requestDTO.getAppointmentDateTime(), settings);
        
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

        Optional<Appointment> existing = appointmentRepository
                .findFirstByCustomerIdAndAppointmentDateTimeOrderByIdDesc(
                        customer.getId(), requestDTO.getAppointmentDateTime());
        if (existing.isPresent()) {
            Appointment existingAppointment = existing.get();
            boolean reservationIsActive = existingAppointment.getStatus() == Appointment.AppointmentStatus.PENDING
                    && existingAppointment.getPaymentStatus() == Appointment.PaymentStatus.PENDING
                    && (existingAppointment.getPaymentPendingExpiresAt() == null
                    || existingAppointment.getPaymentPendingExpiresAt().isAfter(LocalDateTime.now()));
            if (reservationIsActive) {
                AppointmentResponseDTO response = mapToResponseDTO(existingAppointment);
                response.setPaymentToken(bookingPaymentTokenService.createToken(existingAppointment.getId()));
                return response;
            }
            boolean appointmentIsActive = existingAppointment.getStatus() == Appointment.AppointmentStatus.APPROVED
                    || existingAppointment.getPaymentStatus() == Appointment.PaymentStatus.AUTHORIZED
                    || existingAppointment.getPaymentStatus() == Appointment.PaymentStatus.CAPTURED;
            if (appointmentIsActive) {
                throw new IllegalStateException("You already have an active appointment at this date and time");
            }
            if (existingAppointment.getStatus() == Appointment.AppointmentStatus.PENDING) {
                existingAppointment.setStatus(Appointment.AppointmentStatus.CANCELLED);
                existingAppointment.setPaymentStatus(Appointment.PaymentStatus.CANCELLED);
                existingAppointment.setAdminNotes("Replaced after an incomplete or expired booking attempt");
                appointmentRepository.saveAndFlush(existingAppointment);
            }
        }

        validateAppointmentAvailability(requestDTO.getAppointmentDateTime(), settings);

        Appointment appointment = new Appointment();
        appointment.setCustomer(customer);
        appointment.setService(service);
        appointment.setAppointmentDateTime(requestDTO.getAppointmentDateTime());
        appointment.setAppointmentEndDateTime(null);
        appointment.setNotes(requestDTO.getNotes());
        appointment.setSelectedService(service.getName());
        appointment.setSelectedSize(requestDTO.getSelectedSize());
        appointment.setSelectedLength(lengthOption != null ? lengthOption.getName() : requestDTO.getSelectedLength());
        appointment.setSelectedFoundation(foundation);
        appointment.setSelectedTexture(resolveTexture(service, requestDTO.getSelectedTexture()));
        appointment.setPrice(MoneySupport.fromCents(quote.priceCents()));
        appointment.setDepositAmount(quote.depositCents());
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
            .orElseThrow(() -> new org.example.backendbraiding.exception.ResourceNotFoundException("Appointment not found"));

        if (appointment.getStatus() != Appointment.AppointmentStatus.PENDING) {
            throw new IllegalStateException("Only pending appointments can be approved");
        }
        if (appointment.getApprovedAt() != null) {
            throw new IllegalStateException("Payment capture is already processing for this appointment");
        }
        if (!appointment.getAppointmentDateTime().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("Past appointments cannot be approved");
        }
        if (appointment.getPaymentStatus() != Appointment.PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException("Payment must be authorized before approving an appointment");
        }

        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new RuntimeException("Admin not found"));

        // Keep the existing PENDING database status until Stripe confirms capture.
        // approvedAt marks this request as capture-in-progress without requiring a
        // new enum value that older PostgreSQL check constraints reject.
        appointment.setStatus(Appointment.AppointmentStatus.PENDING);
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
        if (appointment.getApprovedAt() != null) {
            throw new IllegalStateException("This appointment cannot be denied while payment capture is processing");
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
        boolean smsSent = smsService.sendAppointmentDeniedSms(
            appointment.getCustomer().getPhoneNumber(),
            customerName,
            actionDTO.getAdminNotes()
        );
        boolean emailSent = emailService.sendAppointmentUpdate(appointment.getCustomer().getEmail(), "Appointment request update",
                "Your appointment request could not be approved. "
                        + (actionDTO.getAdminNotes() == null ? "Please contact the salon." : actionDTO.getAdminNotes()));
        recordNotificationResult(updatedAppointment, emailSent, smsSent);
        
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

    public Page<AppointmentResponseDTO> getWorkflowAppointments(
            String view, String detail, String query, Pageable pageable) {
        Specification<Appointment> specification = workflowSpecification(view, detail, query);
        return appointmentRepository.findAll(specification, pageable).map(this::mapToResponseDTO);
    }

    public Map<String, Long> getWorkflowCounts() {
        return Map.of(
                "NEEDS_ACTION", appointmentRepository.count(workflowSpecification("NEEDS_ACTION", "ALL", "")),
                "UPCOMING", appointmentRepository.count(workflowSpecification("UPCOMING", "ALL", "")),
                "HISTORY", appointmentRepository.count(workflowSpecification("HISTORY", "ALL", ""))
        );
    }

    private Specification<Appointment> workflowSpecification(String requestedView, String requestedDetail, String query) {
        String view = requestedView == null ? "NEEDS_ACTION" : requestedView.toUpperCase(Locale.ROOT);
        String detail = requestedDetail == null ? "ALL" : requestedDetail.toUpperCase(Locale.ROOT);
        LocalDateTime now = ZonedDateTime.now(ZoneId.of("America/Chicago")).toLocalDateTime();

        return (root, criteriaQuery, cb) -> {
            Predicate pending = cb.equal(root.get("status"), Appointment.AppointmentStatus.PENDING);
            Predicate approved = cb.equal(root.get("status"), Appointment.AppointmentStatus.APPROVED);
            Predicate captureProcessing = cb.and(pending, cb.isNotNull(root.get("approvedAt")));
            Predicate paymentIssue = root.get("paymentStatus").in(
                    Appointment.PaymentStatus.CAPTURE_FAILED,
                    Appointment.PaymentStatus.CANCELLATION_FAILED,
                    Appointment.PaymentStatus.FAILED);
            Predicate workflow = switch (view) {
                case "UPCOMING" -> cb.or(
                        captureProcessing,
                        cb.and(approved, cb.greaterThanOrEqualTo(root.get("appointmentDateTime"), now)));
                case "HISTORY" -> cb.or(
                        root.get("status").in(
                                Appointment.AppointmentStatus.DENIED,
                                Appointment.AppointmentStatus.CANCELLED,
                                Appointment.AppointmentStatus.COMPLETED),
                        cb.and(approved, cb.lessThan(root.get("appointmentDateTime"), now)));
                case "NEEDS_ACTION" -> cb.or(pending, paymentIssue);
                default -> throw new IllegalArgumentException(
                        "Invalid appointment workflow view. Valid values are: NEEDS_ACTION, UPCOMING, HISTORY");
            };

            Predicate detailPredicate = switch (detail) {
                case "ALL" -> cb.conjunction();
                case "READY_FOR_APPROVAL" -> cb.and(
                        pending,
                        cb.isNull(root.get("approvedAt")),
                        cb.equal(root.get("paymentStatus"), Appointment.PaymentStatus.AUTHORIZED),
                        cb.greaterThan(root.get("appointmentDateTime"), now));
                case "AWAITING_PAYMENT" -> cb.and(
                        pending,
                        cb.isNull(root.get("approvedAt")),
                        root.get("paymentStatus").in(
                                Appointment.PaymentStatus.PENDING,
                                Appointment.PaymentStatus.CANCELLED));
                case "CAPTURE_PROCESSING" -> captureProcessing;
                case "PAYMENT_ISSUE" -> paymentIssue;
                case "APPROVED" -> approved;
                case "COMPLETED" -> cb.equal(root.get("status"), Appointment.AppointmentStatus.COMPLETED);
                case "DENIED" -> cb.equal(root.get("status"), Appointment.AppointmentStatus.DENIED);
                case "CANCELLED" -> cb.equal(root.get("status"), Appointment.AppointmentStatus.CANCELLED);
                case "PAST" -> cb.lessThan(root.get("appointmentDateTime"), now);
                default -> throw new IllegalArgumentException("Invalid appointment workflow detail");
            };

            Predicate search = cb.conjunction();
            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                var customer = root.join("customer");
                search = cb.or(
                        cb.like(cb.lower(customer.get("firstName")), pattern),
                        cb.like(cb.lower(customer.get("lastName")), pattern),
                        cb.like(cb.lower(customer.get("email")), pattern),
                        cb.like(cb.lower(customer.get("phoneNumber")), pattern),
                        cb.like(cb.lower(cb.concat(cb.concat(customer.get("firstName"), " "), customer.get("lastName"))), pattern)
                );
            }
            return cb.and(workflow, detailPredicate, search);
        };
    }

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = {"appointments", "availableSlots"}, allEntries = true)
    public AppointmentResponseDTO completeAppointment(Long appointmentId, Long adminId, AppointmentActionDTO actionDTO) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new org.example.backendbraiding.exception.ResourceNotFoundException("Appointment not found"));
        if (appointment.getStatus() != Appointment.AppointmentStatus.APPROVED) {
            throw new IllegalStateException("Only approved appointments can be completed");
        }
        LocalDateTime now = ZonedDateTime.now(ZoneId.of("America/Chicago")).toLocalDateTime();
        if (appointment.getAppointmentDateTime().isAfter(now)) {
            throw new IllegalStateException("A future appointment cannot be marked complete");
        }
        appointment.setStatus(Appointment.AppointmentStatus.COMPLETED);
        appointment.setApprovedBy(adminRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found")));
        if (actionDTO.getAdminNotes() != null && !actionDTO.getAdminNotes().isBlank()) {
            appointment.setAdminNotes(actionDTO.getAdminNotes());
        }
        return mapToResponseDTO(appointmentRepository.save(appointment));
    }

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = {"appointments", "availableSlots"}, allEntries = true)
    public AppointmentResponseDTO cancelAppointment(Long appointmentId, Long adminId, AppointmentActionDTO actionDTO) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new org.example.backendbraiding.exception.ResourceNotFoundException("Appointment not found"));
        if (appointment.getStatus() != Appointment.AppointmentStatus.PENDING
                && appointment.getStatus() != Appointment.AppointmentStatus.APPROVED) {
            throw new IllegalStateException("Only pending or approved appointments can be cancelled");
        }
        if (appointment.getStatus() == Appointment.AppointmentStatus.PENDING && appointment.getApprovedAt() != null) {
            throw new IllegalStateException("Wait for payment capture to finish before cancelling this appointment");
        }
        if (actionDTO.getAdminNotes() == null || actionDTO.getAdminNotes().isBlank()) {
            throw new IllegalArgumentException("A cancellation reason is required");
        }

        appointment.setStatus(Appointment.AppointmentStatus.CANCELLED);
        appointment.setApprovedBy(adminRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found")));
        appointment.setApprovedAt(LocalDateTime.now());
        appointment.setAdminNotes(actionDTO.getAdminNotes().trim());
        Appointment saved = appointmentRepository.save(appointment);

        if (saved.getPaymentIntentId() != null
                && saved.getPaymentStatus() == Appointment.PaymentStatus.AUTHORIZED) {
            String paymentIntentId = saved.getPaymentIntentId();
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

        boolean smsSent = smsService.sendSms(saved.getCustomer().getPhoneNumber(),
                "Hi " + saved.getCustomer().getFirstName()
                        + ", your appointment was cancelled by the salon. Reason: " + saved.getAdminNotes());
        boolean emailSent = emailService.sendAppointmentUpdate(saved.getCustomer().getEmail(), "Appointment cancelled",
                "Your appointment was cancelled by the salon. Reason: " + saved.getAdminNotes());
        recordNotificationResult(saved, emailSent, smsSent);
        return mapToResponseDTO(saved);
    }

    @Transactional
    public AppointmentResponseDTO retryNotification(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new org.example.backendbraiding.exception.ResourceNotFoundException("Appointment not found"));
        boolean smsSent;
        boolean emailSent;
        String name = appointment.getCustomer().getFirstName();
        if (appointment.getStatus() == Appointment.AppointmentStatus.APPROVED) {
            smsSent = smsService.sendAppointmentApprovedSms(
                    appointment.getCustomer().getPhoneNumber(), name, appointment.getAppointmentDateTime().toString());
            emailSent = emailService.sendAppointmentUpdate(
                    appointment.getCustomer().getEmail(), "Appointment approved",
                    "Your appointment for " + appointment.getAppointmentDateTime() + " Central Time has been approved.");
        } else if (appointment.getStatus() == Appointment.AppointmentStatus.DENIED) {
            smsSent = smsService.sendAppointmentDeniedSms(
                    appointment.getCustomer().getPhoneNumber(), name, appointment.getAdminNotes());
            emailSent = emailService.sendAppointmentUpdate(
                    appointment.getCustomer().getEmail(), "Appointment request update",
                    "Your appointment request could not be approved. " + appointment.getAdminNotes());
        } else if (appointment.getStatus() == Appointment.AppointmentStatus.CANCELLED) {
            String message = "Your appointment was cancelled by the salon. Reason: " + appointment.getAdminNotes();
            smsSent = smsService.sendSms(appointment.getCustomer().getPhoneNumber(), "Hi " + name + ", " + message);
            emailSent = emailService.sendAppointmentUpdate(
                    appointment.getCustomer().getEmail(), "Appointment cancelled", message);
        } else {
            throw new IllegalStateException("Notifications can only be retried for approved, denied, or cancelled appointments");
        }
        recordNotificationResult(appointment, emailSent, smsSent);
        return mapToResponseDTO(appointment);
    }

    private void recordNotificationResult(Appointment appointment, boolean emailSent, boolean smsSent) {
        appointment.setNotificationStatus(emailSent && smsSent ? "SENT"
                : !emailSent && !smsSent ? "FAILED"
                : emailSent ? "SMS_FAILED" : "EMAIL_FAILED");
        appointment.setNotificationLastAttemptAt(LocalDateTime.now());
        appointmentRepository.save(appointment);
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
            // Backward compatibility for clients briefly deployed with the
            // capture-specific status. Capture-in-progress appointments now
            // remain PENDING and are identified by approvedAt.
            if ("APPROVAL_PENDING_CAPTURE".equalsIgnoreCase(status)) {
                return appointmentRepository.findByStatus(Appointment.AppointmentStatus.PENDING, pageable)
                        .map(this::mapToResponseDTO);
            }
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
        dto.setSelectedFoundation(appointment.getSelectedFoundation());
        dto.setSelectedTexture(appointment.getSelectedTexture());
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
        dto.setPaymentAuthorizationExpiresAt(appointment.getPaymentAuthorizationExpiresAt());
        dto.setPaymentMethodLast4(appointment.getPaymentMethodLast4());
        dto.setPaymentMethodBrand(appointment.getPaymentMethodBrand());
        dto.setNotificationStatus(appointment.getNotificationStatus());
        dto.setNotificationLastAttemptAt(appointment.getNotificationLastAttemptAt());

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

    private void validateQuote(BookingQuoteTokenService.QuoteClaims quote, ServiceItem service,
                               LengthOption lengthOption, String foundation) {
        if (!service.getId().equals(quote.serviceId())) {
            throw new IllegalArgumentException("The booking quote does not match the selected service");
        }
        Long selectedLengthId = lengthOption == null ? null : lengthOption.getId();
        if (!java.util.Objects.equals(selectedLengthId, quote.lengthOptionId())) {
            throw new IllegalArgumentException("The booking quote does not match the selected length");
        }
        if (!java.util.Objects.equals(foundation, quote.foundation())) {
            throw new IllegalArgumentException("The booking quote does not match the selected braid foundation");
        }
        long currentVersion = service.getVersion() == null ? 0L : service.getVersion();
        if (currentVersion != quote.serviceVersion()) {
            throw new IllegalStateException("Pricing changed while you were booking. Please review the updated price.");
        }
        String basePrice = lengthOption == null ? service.getPrice() : lengthOption.getPrice();
        long currentPriceCents = MoneySupport.requirePositiveCents(
                priceForFoundation(basePrice, service, foundation), "Selected price");
        if (currentPriceCents != quote.priceCents()) {
            throw new IllegalStateException("Pricing changed while you were booking. Please review the updated price.");
        }
        if (quote.depositCents() <= 0 || quote.depositCents() > quote.priceCents()) {
            throw new IllegalArgumentException("The booking quote contains an invalid deposit");
        }
    }

    static String resolveFoundation(ServiceItem service, String selectedFoundation) {
        if (!Boolean.TRUE.equals(service.getFoundationChoicesEnabled())) {
            if (selectedFoundation != null && !selectedFoundation.isBlank()) {
                throw new IllegalArgumentException("This service does not offer braid foundation choices");
            }
            return null;
        }
        if (selectedFoundation == null || selectedFoundation.isBlank()) {
            throw new IllegalArgumentException("Choose a braid foundation");
        }
        String normalized = selectedFoundation.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("REGULAR") && !normalized.equals("KNOTLESS")) {
            throw new IllegalArgumentException("Selected braid foundation is not available");
        }
        return normalized;
    }

    static String priceForFoundation(String basePrice, ServiceItem service, String foundation) {
        if (!"KNOTLESS".equals(foundation)) return basePrice;
        try {
            BigDecimal base = new BigDecimal(basePrice.replace("$", "").trim());
            String adjustmentValue = service.getKnotlessPriceAdjustment();
            BigDecimal adjustment = new BigDecimal(adjustmentValue == null || adjustmentValue.isBlank() ? "0" : adjustmentValue.replace("$", "").trim());
            return base.add(adjustment).stripTrailingZeros().toPlainString();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Service foundation pricing is invalid");
        }
    }

    static String resolveTexture(ServiceItem service, String selectedTexture) {
        List<String> available = service.getHairTextures() == null ? List.of() : service.getHairTextures();
        if (available.isEmpty()) {
            if (selectedTexture != null && !selectedTexture.isBlank()) {
                throw new IllegalArgumentException("This service does not offer a hair texture selection");
            }
            return null;
        }
        if (selectedTexture == null || selectedTexture.isBlank()) {
            throw new IllegalArgumentException("Hair texture is required for this service");
        }
        return available.stream()
                .filter(texture -> texture.equalsIgnoreCase(selectedTexture.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Selected hair texture is not available for this service"));
    }

}
