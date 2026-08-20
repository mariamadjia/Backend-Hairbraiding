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
    private final ServiceItemRepository serviceItemRepository;
    private final AdminRepository adminRepository;
    private final AppointmentSettingsRepository settingsRepository;
    private final PaymentService paymentService;
    private final BusinessHoursRepository businessHoursRepository;
    private final BlockedTimeSlotRepository blockedTimeSlotRepository;
    private final BookingPaymentTokenService bookingPaymentTokenService;
    private final BookingQuoteTokenService bookingQuoteTokenService;
    private final AddOnService addOnService;
    private final TimeSlotRepository timeSlotRepository;
    private final EntityManager entityManager;
    private final AppointmentEventService appointmentEventService;
    private final NotificationOutboxService notificationOutboxService;
    private final AppointmentNotificationTemplates notificationTemplates;
    private final NoShowService noShowService;
    private final NoShowFeeRepository noShowFeeRepository;
    private final AppointmentManagementTokenService managementTokenService;
    private final AppointmentNotificationDispatchService notificationDispatchService;
    private final NotificationOutboxClaimService notificationOutboxClaimService;

    private static final int RESERVATION_TTL_MINUTES = 15;
    private static final String DEPOSIT_POLICY_VERSION = "non-refundable-v1";
    private static final String OFF_SESSION_POLICY_VERSION = "no-show-60-percent-v1";

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
        List<AddOnService.ResolvedAddOn> selectedAddOns = addOnService.validateClaims(
                service, lengthOption, quote.addOns());
        validateQuote(quote, service, lengthOption, foundation, selectedAddOns, settings);
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
        if (noShowFeeRepository.hasUnresolvedBalance(customer.getId())) {
            throw new IllegalStateException("A previous no-show balance must be resolved before booking another appointment");
        }

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

        lockAppointmentSlot(requestDTO.getAppointmentDateTime());
        int durationMinutes = serviceDurationMinutes(service);
        validateAppointmentAvailability(requestDTO.getAppointmentDateTime(), settings, durationMinutes);

        Appointment appointment = new Appointment();
        appointment.setCustomer(customer);
        appointment.setService(service);
        appointment.setAppointmentDateTime(requestDTO.getAppointmentDateTime());
        appointment.setAppointmentEndDateTime(requestDTO.getAppointmentDateTime()
                .plusMinutes(durationMinutes + bufferMinutes(settings)));
        appointment.setNotes(requestDTO.getNotes());
        appointment.setSelectedService(displayServiceName(service));
        appointment.setSelectedSize(service.getName());
        appointment.setSelectedLength(lengthOption != null ? lengthOption.getName() : requestDTO.getSelectedLength());
        appointment.setSelectedFoundation(foundation);
        appointment.setSelectedTexture(resolveTexture(service, requestDTO.getSelectedTexture()));
        appointment.setPrice(MoneySupport.fromCents(quote.priceCents()));
        appointment.setDepositAmount(quote.depositCents());
        appointment.setDepositPolicyVersion(DEPOSIT_POLICY_VERSION);
        appointment.setDepositPolicyAcceptedAt(LocalDateTime.now());
        appointment.setOffSessionConsentPolicyVersion(OFF_SESSION_POLICY_VERSION);
        appointment.setOffSessionConsentAt(LocalDateTime.now());
        customer.setOffSessionConsentPolicyVersion(OFF_SESSION_POLICY_VERSION);
        customer.setOffSessionConsentAt(LocalDateTime.now());
        customerRepository.save(customer);
        appointment.setDurationMinutes(durationMinutes);
        appointment.setStatus(Appointment.AppointmentStatus.PENDING);
        appointment.setPaymentPendingExpiresAt(LocalDateTime.now().plusMinutes(RESERVATION_TTL_MINUTES));

        for (int index = 0; index < selectedAddOns.size(); index++) {
            AddOnService.ResolvedAddOn resolved = selectedAddOns.get(index);
            AppointmentAddOn snapshot = new AppointmentAddOn();
            snapshot.setAppointment(appointment);
            snapshot.setAddOn(resolved.assignment().getAddOn());
            snapshot.setAddOnName(resolved.assignment().getAddOn().getName());
            snapshot.setPricingMode(resolved.assignment().getAddOn().getPricingMode());
            snapshot.setAdvertisedPriceCents(resolved.advertisedPriceCents());
            snapshot.setChargedPriceCents(resolved.chargedPriceCents());
            snapshot.setDisplayOrder(index);
            appointment.getAddOns().add(snapshot);
        }

        Appointment savedAppointment = appointmentRepository.save(appointment);
        appointmentEventService.record(savedAppointment, "CREATED", null, null);
        AppointmentResponseDTO response = mapToResponseDTO(savedAppointment);
        response.setPaymentToken(bookingPaymentTokenService.createToken(savedAppointment.getId()));
        return response;
    }

    static String displayServiceName(ServiceItem service) {
        if (service.getSubcategory() != null
                && service.getSubcategory().getName() != null
                && !service.getSubcategory().getName().isBlank()) {
            return service.getSubcategory().getName().trim();
        }
        return service.getName();
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
            appointment.setStatus(Appointment.AppointmentStatus.CANCELLED);
            if (appointment.getPaymentIntentId() == null) {
                appointment.setPaymentStatus(Appointment.PaymentStatus.CANCELLED);
            }
            appointment.setAdminNotes("Automatically cancelled: payment was not completed in time");
            appointmentRepository.save(appointment);
            appointmentEventService.record(appointment, "AUTOMATICALLY_CANCELLED", null, appointment.getAdminNotes());
            String expiredPaymentIntentId = appointment.getPaymentIntentId();
            Long expiredAppointmentId = appointment.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    if (expiredPaymentIntentId != null) {
                        try {
                            paymentService.cancelPayment(expiredPaymentIntentId);
                        } catch (Exception exception) {
                            paymentService.markCancellationFailed(expiredPaymentIntentId, exception.getMessage());
                        }
                    }
                    notificationDispatchService.expired(expiredAppointmentId);
                }
            });
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
        LocalDateTime now = salonNow();
        if (!appointment.getAppointmentDateTime().isAfter(now)) {
            throw new IllegalStateException("Past appointments cannot be approved");
        }
        if (appointment.getPaymentStatus() != Appointment.PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException("Payment must be authorized before approving an appointment");
        }
        if (PaymentLifecycleRules.isAuthorizationExpired(
                appointment.getPaymentAuthorizationExpiresAt(), LocalDateTime.now())) {
            throw new IllegalStateException("Payment authorization has expired; the customer must authorize payment again");
        }

        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new RuntimeException("Admin not found"));

        // Keep the existing PENDING database status until Stripe confirms capture.
        // approvedAt marks this request as capture-in-progress without requiring a
        // new enum value that older PostgreSQL check constraints reject.
        appointment.setStatus(Appointment.AppointmentStatus.PENDING);
        appointment.setApprovedBy(admin);
        appointment.setApprovedAt(now);
        
        if (actionDTO.getAdminNotes() != null) {
            appointment.setAdminNotes(actionDTO.getAdminNotes());
        }

        Appointment updatedAppointment = appointmentRepository.save(appointment);
        appointmentEventService.record(updatedAppointment, "APPROVAL_REQUESTED", admin, actionDTO.getAdminNotes());

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
        appointment.setApprovedAt(salonNow());
        
        if (actionDTO.getAdminNotes() != null) {
            appointment.setAdminNotes(actionDTO.getAdminNotes());
        }

        Appointment updatedAppointment = appointmentRepository.save(appointment);
        appointmentEventService.record(updatedAppointment, "DENIED", admin, actionDTO.getAdminNotes());
        
        String denialPaymentIntentId = appointment.getPaymentIntentId();
        boolean releaseDenialAuthorization = denialPaymentIntentId != null
                && appointment.getPaymentStatus() == Appointment.PaymentStatus.AUTHORIZED;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (releaseDenialAuthorization) {
                    try {
                        paymentService.cancelPayment(denialPaymentIntentId);
                    } catch (Exception e) {
                        paymentService.markCancellationFailed(denialPaymentIntentId, e.getMessage());
                    }
                }
                notificationDispatchService.denied(updatedAppointment.getId());
            }
        });
        
        return mapToResponseDTO(updatedAppointment);
    }

    @Transactional(readOnly = true)
    public Page<AppointmentResponseDTO> getPendingAppointments(Pageable pageable) {
        return appointmentRepository.findByStatus(Appointment.AppointmentStatus.PENDING, pageable)
            .map(this::mapToResponseDTO);
    }

    @Transactional(readOnly = true)
    public Page<AppointmentResponseDTO> getAllAppointments(Pageable pageable) {
        return appointmentRepository.findAll(pageable)
            .map(this::mapToResponseDTO);
    }

    @Transactional(readOnly = true)
    public Page<AppointmentResponseDTO> getWorkflowAppointments(
            String view, String detail, String query, Pageable pageable) {
        Specification<Appointment> specification = workflowSpecification(view, detail, query);
        return appointmentRepository.findAll(specification, pageable).map(this::mapToResponseDTO);
    }

    @Transactional(readOnly = true)
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
        LocalDateTime now = salonNow();

        return (root, criteriaQuery, cb) -> {
            Predicate pending = cb.equal(root.get("status"), Appointment.AppointmentStatus.PENDING);
            Predicate approved = cb.equal(root.get("status"), Appointment.AppointmentStatus.APPROVED);
            Predicate captureProcessing = cb.and(pending, cb.isNotNull(root.get("approvedAt")));
            Predicate paymentIssue = root.get("paymentStatus").in(
                    Appointment.PaymentStatus.CAPTURE_FAILED,
                    Appointment.PaymentStatus.CANCELLATION_FAILED,
                    Appointment.PaymentStatus.FAILED);
            Predicate notificationIssue = cb.like(
                    cb.lower(cb.coalesce(root.get("notificationStatus"), "")), "%failed%");
            var noShowIssueQuery = criteriaQuery.subquery(Long.class);
            var noShowFee = noShowIssueQuery.from(NoShowFee.class);
            noShowIssueQuery.select(noShowFee.get("id")).where(
                    cb.equal(noShowFee.get("appointment"), root),
                    noShowFee.get("paymentStatus").in(
                            NoShowFee.PaymentStatus.UNPAID,
                            NoShowFee.PaymentStatus.PROCESSING,
                            NoShowFee.PaymentStatus.FAILED),
                    cb.notEqual(noShowFee.get("feeDecision"), NoShowFee.FeeDecision.WAIVED));
            Predicate noShowPaymentIssue = cb.exists(noShowIssueQuery);
            Predicate workflow = switch (view) {
                case "UPCOMING" -> cb.or(
                        captureProcessing,
                        cb.and(approved, cb.greaterThanOrEqualTo(root.get("appointmentDateTime"), now)));
                case "HISTORY" -> cb.or(
                        root.get("status").in(
                                Appointment.AppointmentStatus.DENIED,
                                Appointment.AppointmentStatus.CANCELLED,
                                Appointment.AppointmentStatus.COMPLETED,
                                Appointment.AppointmentStatus.NO_SHOW),
                        cb.and(approved, cb.lessThan(root.get("appointmentDateTime"), now)));
                case "NEEDS_ACTION" -> cb.or(pending, paymentIssue, noShowPaymentIssue, notificationIssue);
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
                case "PAYMENT_ISSUE" -> cb.or(paymentIssue, noShowPaymentIssue, notificationIssue);
                case "APPROVED" -> approved;
                case "COMPLETED" -> cb.equal(root.get("status"), Appointment.AppointmentStatus.COMPLETED);
                case "DENIED" -> cb.equal(root.get("status"), Appointment.AppointmentStatus.DENIED);
                case "CANCELLED" -> cb.equal(root.get("status"), Appointment.AppointmentStatus.CANCELLED);
                case "NO_SHOW" -> cb.equal(root.get("status"), Appointment.AppointmentStatus.NO_SHOW);
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
        LocalDateTime now = salonNow();
        if (appointment.getAppointmentDateTime().isAfter(now)) {
            throw new IllegalStateException("A future appointment cannot be marked complete");
        }
        appointment.setStatus(Appointment.AppointmentStatus.COMPLETED);
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        appointment.setApprovedBy(admin);
        if (actionDTO.getAdminNotes() != null && !actionDTO.getAdminNotes().isBlank()) {
            appointment.setAdminNotes(actionDTO.getAdminNotes());
        }
        Appointment saved = appointmentRepository.save(appointment);
        appointmentEventService.record(saved, "COMPLETED", admin, actionDTO.getAdminNotes());
        return mapToResponseDTO(saved);
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
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        appointment.setApprovedBy(admin);
        appointment.setApprovedAt(salonNow());
        appointment.setAdminNotes(actionDTO.getAdminNotes().trim());
        Appointment saved = appointmentRepository.save(appointment);
        appointmentEventService.record(saved, "CANCELLED", admin, actionDTO.getAdminNotes());

        String cancellationPaymentIntentId = saved.getPaymentIntentId();
        boolean releaseCancellationAuthorization = cancellationPaymentIntentId != null
                && saved.getPaymentStatus() == Appointment.PaymentStatus.AUTHORIZED;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (releaseCancellationAuthorization) {
                    try {
                        paymentService.cancelPayment(cancellationPaymentIntentId);
                    } catch (Exception e) {
                        paymentService.markCancellationFailed(cancellationPaymentIntentId, e.getMessage());
                    }
                }
                notificationDispatchService.cancelled(saved.getId());
            }
        });
        return mapToResponseDTO(saved);
    }

    @Transactional
    public AppointmentResponseDTO retryNotification(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new org.example.backendbraiding.exception.ResourceNotFoundException("Appointment not found"));
        if (!notificationOutboxClaimService.retryLatestFailed(appointmentId))
            throw new IllegalStateException("There is no failed notification channel to retry");
        return mapToResponseDTO(appointment);
    }

    private void enqueueBoth(Appointment appointment, AppointmentNotificationTemplates.Notification notification) {
        notificationOutboxService.enqueueBoth(appointment, notification.subject(), notification.emailBody(), notification.smsBody());
    }

    @Transactional(readOnly = true)
    public Page<AppointmentResponseDTO> getUpcomingAppointments(Pageable pageable) {
        LocalDateTime salonNow = salonNow();
        return appointmentRepository.findActiveUpcomingAppointments(salonNow, pageable)
                .map(this::mapToResponseDTO);
    }

    @Transactional(readOnly = true)
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
            throw new IllegalArgumentException("Invalid appointment status: " + status + ". Valid values are: PENDING, APPROVED, DENIED, CANCELLED, COMPLETED, NO_SHOW");
        }
    }

    @Transactional(readOnly = true)
    public AppointmentResponseDTO getAppointmentById(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Appointment not found"));
        return mapToResponseDTO(appointment);
    }

    @Transactional(readOnly = true)
    public Page<AppointmentResponseDTO> getAppointmentsByDateRange(
            LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        if (!endDate.isAfter(startDate)) throw new IllegalArgumentException("End date must be after start date");
        if (!AppointmentManagementRules.isValidDateRange(startDate, endDate))
            throw new IllegalArgumentException("Date range cannot exceed 366 days");
        return appointmentRepository.findAppointmentsBetweenDates(startDate, endDate, pageable)
                .map(this::mapToResponseDTO);
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
            dto.setStyleName(appointment.getService().getSubcategory() != null
                    ? appointment.getService().getSubcategory().getName()
                    : appointment.getService().getName());
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
        dto.setAmountAuthorized(appointment.getAmountAuthorized());
        dto.setAmountCaptured(appointment.getAmountCaptured());
        dto.setPaymentCapturedAt(appointment.getPaymentCapturedAt());
        dto.setPaymentAuthorizationExpiresAt(appointment.getPaymentAuthorizationExpiresAt());
        dto.setPaymentMethodLast4(appointment.getPaymentMethodLast4());
        dto.setPaymentMethodBrand(appointment.getPaymentMethodBrand());
        dto.setDepositPolicyVersion(appointment.getDepositPolicyVersion());
        dto.setDepositPolicyAcceptedAt(appointment.getDepositPolicyAcceptedAt());
        dto.setNotificationStatus(appointment.getNotificationStatus());
        dto.setNotificationLastAttemptAt(appointment.getNotificationLastAttemptAt());
        dto.setCancelledByCustomer(appointment.getCancelledByCustomer());
        dto.setCustomerCancellationReason(appointment.getCustomerCancellationReason());
        dto.setSelfServiceChangeCount(appointment.getSelfServiceChangeCount());
        dto.setLastSelfServiceChangeAt(appointment.getLastSelfServiceChangeAt());
        dto.setRescheduledFromDateTime(appointment.getRescheduledFromDateTime());
        dto.setAddOns(appointment.getAddOns().stream().map(item ->
                new org.example.backendbraiding.dto.QuotedAddOnDTO(
                        item.getAddOn() == null ? null : item.getAddOn().getId(), item.getAddOnName(),
                        item.getPricingMode(), item.getAdvertisedPriceCents(), item.getChargedPriceCents(),
                        "STARTING_AT".equals(item.getPricingMode()))).toList());
        dto.setNoShowFee(noShowService.preview(appointment));

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
        if (!java.util.Objects.equals(settings.getVersion() == null ? 0L : settings.getVersion(), dto.getVersion())) {
            throw new org.springframework.dao.OptimisticLockingFailureException(
                    "Booking rules changed in another session. Reload before saving.");
        }
        
        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new RuntimeException("Admin not found"));
        
        settings.setSlotDurationMinutes(dto.getSlotDurationMinutes());
        settings.setAdvanceBookingDays(dto.getAdvanceBookingDays());
        settings.setMaxAppointmentsPerSlot(dto.getMaxAppointmentsPerSlot());
        settings.setRequireApproval(dto.getRequireApproval());
        settings.setAllowSameDayBooking(dto.getAllowSameDayBooking());
        settings.setBufferTimeBetweenAppointments(dto.getBufferTimeBetweenAppointments());
        try {
            settings.setTimezone(ZoneId.of(dto.getTimezone().trim()).getId());
        } catch (Exception exception) {
            throw new IllegalArgumentException("Timezone must be a valid IANA timezone, such as America/Chicago");
        }
        settings.setUpdatedAt(LocalDateTime.now());
        settings.setUpdatedBy(admin);
        
        settings = settingsRepository.save(settings);

        return mapToSettingsDTO(settings);
    }

    private AppointmentSettingsDTO mapToSettingsDTO(AppointmentSettings settings) {
        AppointmentSettingsDTO dto = new AppointmentSettingsDTO();
        dto.setVersion(settings.getVersion() == null ? 0L : settings.getVersion());
        dto.setSlotDurationMinutes(settings.getSlotDurationMinutes());
        dto.setAdvanceBookingDays(settings.getAdvanceBookingDays());
        dto.setMaxAppointmentsPerSlot(settings.getMaxAppointmentsPerSlot());
        dto.setRequireApproval(settings.getRequireApproval());
        dto.setAllowSameDayBooking(settings.getAllowSameDayBooking());
        dto.setBufferTimeBetweenAppointments(settings.getBufferTimeBetweenAppointments());
        dto.setTimezone(settings.getTimezone());
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
    
    private void validateAppointmentAvailability(LocalDateTime appointmentDateTime, AppointmentSettings settings,
                                                 int serviceDurationMinutes) {
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

        int occupiedMinutes = serviceDurationMinutes + bufferMinutes(settings);
        LocalDateTime slotEnd = appointmentDateTime.plusMinutes(occupiedMinutes);
        if (slotEnd.isAfter(businessClose)) {
            throw new IllegalArgumentException("Appointment slot and buffer must end before business closing time");
        }

        List<TimeSlot> configuredSlots = timeSlotRepository.findByDayOfWeekOrderBySlotOrderAsc(
                appointmentDateTime.getDayOfWeek().name());
        if (configuredSlots.isEmpty()) {
            long minutesFromOpening = java.time.Duration.between(businessOpen, appointmentDateTime).toMinutes();
            if (minutesFromOpening % slotIntervalMinutes(settings) != 0) {
                throw new IllegalArgumentException("Appointment time must match an available slot");
            }
        }
        List<BlockedTimeSlot> blockedSlots = blockedTimeSlotRepository.findOverlappingSlots(appointmentDateTime, slotEnd);
        blockedTimeSlotRepository.findByIsRecurringTrue().stream()
            .filter(block -> BookingRules.recurringBlockOverlaps(block, appointmentDateTime, slotEnd))
            .forEach(blockedSlots::add);
        if (!blockedSlots.isEmpty()) {
            throw new IllegalStateException("This time slot is blocked: " + blockedSlots.get(0).getReason());
        }
        
        int capacity = maximumCapacity(settings);
        if (!configuredSlots.isEmpty()) {
            int configuredIndex = -1;
            for (int index = 0; index < configuredSlots.size(); index++) {
                if (configuredSlots.get(index).getStartTime().equals(appointmentDateTime.toLocalTime())) {
                    configuredIndex = index;
                    break;
                }
            }
            if (configuredIndex < 0) throw new IllegalArgumentException("Appointment time is not a configured slot");
            TimeSlot configured = configuredSlots.get(configuredIndex);
            LocalTime contiguousEnd = configured.getEndTime();
            for (int index = configuredIndex + 1; index < configuredSlots.size(); index++) {
                TimeSlot next = configuredSlots.get(index);
                if (!next.getStartTime().equals(contiguousEnd)) break;
                contiguousEnd = next.getEndTime();
            }
            LocalDateTime configuredEnd = LocalDateTime.of(appointmentDateTime.toLocalDate(), contiguousEnd);
            if (appointmentDateTime.plusMinutes(occupiedMinutes).isAfter(configuredEnd)) {
                throw new IllegalArgumentException("The selected service does not fit in this availability window");
            }
            capacity = configured.getCapacity() == null || configured.getCapacity() < 1
                    ? 1 : configured.getCapacity();
        }

        LocalDateTime salonNow = ZonedDateTime.now(salonZone(settings)).toLocalDateTime();
        long appointmentCount = appointmentRepository.countOverlapping(
                appointmentDateTime, appointmentDateTime.plusMinutes(occupiedMinutes), salonNow);
        if (appointmentCount >= capacity) {
            throw new IllegalStateException("This time slot is fully booked");
        }
    }

    private void lockAppointmentSlot(LocalDateTime appointmentDateTime) {
        // Lock the entire salon date, not only one start. Long services can
        // overlap a different start time on the same day.
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(?1, 0))")
                .setParameter(1, "appointment-date:" + appointmentDateTime.toLocalDate())
                .getSingleResult();
    }

    private int serviceDurationMinutes(ServiceItem service) {
        Integer configured = service.getDurationMinutes();
        return configured == null || configured < 15 ? 60 : configured;
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

    private int bufferMinutes(AppointmentSettings settings) {
        Integer configured = settings.getBufferTimeBetweenAppointments();
        return configured == null || configured < 0 ? 0 : configured;
    }

    private LocalDateTime salonNow() {
        AppointmentSettings settings = settingsRepository.findFirstByOrderByIdDesc().orElseGet(this::createDefaultSettings);
        return ZonedDateTime.now(salonZone(settings)).toLocalDateTime();
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
                               LengthOption lengthOption, String foundation,
                               List<AddOnService.ResolvedAddOn> addOns, AppointmentSettings settings) {
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
        String basePrice = lengthOption == null ? service.getPrice()
                : "KNOTLESS".equals(foundation) && "SEPARATE".equals(service.getKnotlessPricingMode())
                    ? lengthOption.getKnotlessPrice() : lengthOption.getPrice();
        long currentPriceCents = MoneySupport.requirePositiveCents(
                priceForFoundation(basePrice, service, foundation), "Selected price");

        long currentAddOnCents = addOns.stream().mapToLong(AddOnService.ResolvedAddOn::chargedPriceCents).sum();
        if (Math.addExact(currentPriceCents, currentAddOnCents) != quote.priceCents()) {
            throw new IllegalStateException("Pricing changed while you were booking. Please review the updated price.");
        }
        if (quote.depositCents() <= 0 || quote.depositCents() > quote.priceCents()) {
            throw new IllegalArgumentException("The booking quote contains an invalid deposit");
        }
        long configuredDeposit = service.getDepositOverrideCents() != null
                ? service.getDepositOverrideCents()
                : settings.getDefaultDepositCents() == null ? 5000L : settings.getDefaultDepositCents();
        long addOnDepositCents = addOns.stream()
                .mapToLong(AddOnService.ResolvedAddOn::depositAdjustmentCents).sum();
        long currentDepositCents = effectiveDeposit(configuredDeposit, addOnDepositCents, quote.priceCents());
        if (currentDepositCents != quote.depositCents()) {
            throw new IllegalStateException("The deposit changed while you were booking. Please review the updated deposit.");
        }
    }

    static long effectiveDeposit(long configuredDeposit, long addOnDepositCents, long priceCents) {
        if (configuredDeposit <= 0) throw new IllegalStateException("Booking deposit is not configured");
        return Math.min(Math.addExact(configuredDeposit, addOnDepositCents), priceCents);
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
        if ("SEPARATE".equals(service.getKnotlessPricingMode())) return basePrice;
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
