package org.example.backendbraiding.service;

import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.dto.*;
import org.example.backendbraiding.model.Admin;
import org.example.backendbraiding.model.Appointment;
import org.example.backendbraiding.repository.AdminRepository;
import org.example.backendbraiding.repository.AppointmentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class OwnerAppointmentService {
    private static final ZoneId SALON_ZONE = ZoneId.of("America/Chicago");

    private final AppointmentService appointmentService;
    private final AppointmentRepository appointmentRepository;
    private final AdminRepository adminRepository;
    private final PaymentService paymentService;
    private final OwnerDepositTokenService tokenService;
    private final AppointmentManagementTokenService managementTokenService;
    private final AppointmentNotificationTemplates templates;
    private final NotificationOutboxService outboxService;
    private final AppointmentEventService eventService;

    @Value("${app.frontend-url}") private String frontendUrl;

    @Transactional
    public OwnerAppointmentResponse create(OwnerAppointmentRequest request, Long adminId) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Administrator not found"));
        AppointmentRequestDTO base = new AppointmentRequestDTO();
        base.setQuoteToken(request.getQuoteToken());
        base.setFirstName(request.getFirstName());
        base.setLastName(request.getLastName());
        base.setEmail(request.getEmail());
        base.setPhoneNumber(request.getPhoneNumber());
        base.setAppointmentDateTime(request.getAppointmentDateTime());
        base.setServiceId(request.getServiceId());
        base.setLengthOptionId(request.getLengthOptionId());
        base.setSelectedLength(request.getSelectedLength());
        base.setSelectedFoundation(request.getSelectedFoundation());
        base.setSelectedTexture(request.getSelectedTexture());
        base.setNotes(request.getNotes());
        // The owner flow does not record customer policy consent. These values only
        // satisfy the legacy DTO; createOwnerAppointmentBase deliberately skips it.
        base.setDepositPolicyAccepted(true);
        base.setOffSessionConsentAccepted(true);

        AppointmentResponseDTO created = appointmentService.createOwnerAppointmentBase(base);
        Appointment appointment = appointmentRepository.findByIdForUpdate(created.getId())
                .orElseThrow(() -> new IllegalStateException("Created appointment could not be loaded"));
        appointment.setBookingSource(Appointment.BookingSource.OWNER);
        appointment.setCreatedByAdmin(admin);
        boolean depositRequired = Boolean.TRUE.equals(request.getDepositRequired())
                && appointment.getDepositAmount() != null
                && appointment.getDepositAmount() > 0;
        appointment.setDepositRequired(depositRequired);

        if (depositRequired) {
            OwnerDepositTokenService.IssuedToken issued = tokenService.issue(appointment.getId());
            appointment.setOwnerDepositTokenHash(tokenService.hash(issued.value()));
            LocalDateTime expiresAt = LocalDateTime.ofInstant(issued.expiresAt(), SALON_ZONE);
            appointment.setDepositLinkExpiresAt(expiresAt);
            appointment.setPaymentPendingExpiresAt(expiresAt);
            appointmentRepository.saveAndFlush(appointment);
            paymentService.createOwnerDepositIntent(appointment.getId());
            String url = paymentUrl(appointment.getId(), issued.value());
            AppointmentNotificationTemplates.Notification notification =
                    templates.ownerDepositRequested(appointment, url);
            outboxService.enqueueBoth(appointment, notification.subject(), notification.emailBody(), notification.smsBody());
            eventService.record(appointment, "OWNER_CREATED_AWAITING_DEPOSIT", admin, null);
            return response(appointment, url);
        }

        appointment.setDepositAmount(0L);
        appointment.setPaymentStatus(Appointment.PaymentStatus.NOT_REQUIRED);
        appointment.setPaymentPendingExpiresAt(null);
        appointment.setStatus(Appointment.AppointmentStatus.APPROVED);
        appointment.setApprovedBy(admin);
        appointment.setApprovedAt(LocalDateTime.now());
        appointmentRepository.save(appointment);
        String managementUrl = managementTokenService.issue(appointment);
        AppointmentNotificationTemplates.Notification notification =
                templates.ownerConfirmedWithoutDeposit(appointment, managementUrl);
        outboxService.enqueueBoth(appointment, notification.subject(), notification.emailBody(), notification.smsBody());
        eventService.record(appointment, "OWNER_CREATED_CONFIRMED", admin, "Deposit not required");
        return response(appointment, null);
    }

    @Transactional
    public OwnerDepositPageDTO depositPage(Long appointmentId, String token) {
        PaymentIntentResponse intent = paymentService.getOwnerDepositIntent(appointmentId, token);
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));
        long total = MoneySupport.requirePositiveCents(appointment.getPrice(), "Appointment price");
        long deposit = appointment.getDepositAmount() == null ? 0L : appointment.getDepositAmount();
        return OwnerDepositPageDTO.builder()
                .appointmentId(appointment.getId())
                .customerName(appointment.getCustomer().getFirstName() + " " + appointment.getCustomer().getLastName())
                .serviceName(appointment.getSelectedService())
                .selectedSize(appointment.getSelectedSize())
                .selectedLength(appointment.getSelectedLength())
                .selectedFoundation(appointment.getSelectedFoundation())
                .selectedTexture(appointment.getSelectedTexture())
                .addOns(appointment.getAddOns().stream().map(item -> new QuotedAddOnDTO(
                        item.getAddOn() == null ? null : item.getAddOn().getId(), item.getAddOnName(),
                        item.getPricingMode(), item.getAdvertisedPriceCents(), item.getChargedPriceCents(),
                        "STARTING_AT".equals(item.getPricingMode()))).toList())
                .appointmentDateTime(appointment.getAppointmentDateTime())
                .serviceTotalCents(total)
                .depositDueCents(deposit)
                .remainingBalanceCents(Math.max(0L, total - deposit))
                .expiresAt(appointment.getDepositLinkExpiresAt())
                .paymentStatus(appointment.getPaymentStatus().name())
                .appointmentStatus(appointment.getStatus().name())
                .clientSecret(intent.getClientSecret())
                .build();
    }

    @Transactional
    public OwnerAppointmentResponse resend(Long appointmentId, Long adminId) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Administrator not found"));
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));
        requireAwaitingOwnerDeposit(appointment);
        OwnerDepositTokenService.IssuedToken issued = tokenService.issue(appointmentId);
        appointment.setOwnerDepositTokenHash(tokenService.hash(issued.value()));
        LocalDateTime expiresAt = LocalDateTime.ofInstant(issued.expiresAt(), SALON_ZONE);
        appointment.setDepositLinkExpiresAt(expiresAt);
        appointment.setPaymentPendingExpiresAt(expiresAt);
        appointmentRepository.save(appointment);
        String url = paymentUrl(appointmentId, issued.value());
        AppointmentNotificationTemplates.Notification notification = templates.ownerDepositRequested(appointment, url);
        outboxService.enqueueBoth(appointment, notification.subject(), notification.emailBody(), notification.smsBody());
        eventService.record(appointment, "DEPOSIT_LINK_RESENT", admin, null);
        return response(appointment, url);
    }

    private void requireAwaitingOwnerDeposit(Appointment appointment) {
        if (appointment.getBookingSource() != Appointment.BookingSource.OWNER
                || !Boolean.TRUE.equals(appointment.getDepositRequired())
                || appointment.getStatus() != Appointment.AppointmentStatus.PENDING
                || appointment.getPaymentStatus() == Appointment.PaymentStatus.CAPTURED) {
            throw new IllegalStateException("Appointment is not awaiting an owner-sent deposit");
        }
    }

    private OwnerAppointmentResponse response(Appointment appointment, String paymentUrl) {
        long total = MoneySupport.requirePositiveCents(appointment.getPrice(), "Appointment price");
        long deposit = appointment.getDepositAmount() == null ? 0L : appointment.getDepositAmount();
        return OwnerAppointmentResponse.builder()
                .appointmentId(appointment.getId())
                .appointmentStatus(appointment.getStatus().name())
                .paymentStatus(appointment.getPaymentStatus().name())
                .depositRequired(appointment.getDepositRequired())
                .depositAmount(deposit)
                .remainingBalanceCents(Math.max(0L, total - deposit))
                .depositLinkExpiresAt(appointment.getDepositLinkExpiresAt())
                .depositPaymentUrl(paymentUrl)
                .build();
    }

    private String paymentUrl(Long appointmentId, String token) {
        return frontendUrl.replaceAll("/+$", "") + "/pay-deposit/" + appointmentId + "?token=" + token;
    }
}
