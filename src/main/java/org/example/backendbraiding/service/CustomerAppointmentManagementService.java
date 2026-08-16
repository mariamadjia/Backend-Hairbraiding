package org.example.backendbraiding.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.dto.AvailableSlotDTO;
import org.example.backendbraiding.dto.CustomerCancelRequest;
import org.example.backendbraiding.dto.CustomerRescheduleRequest;
import org.example.backendbraiding.dto.ManagedAppointmentDTO;
import org.example.backendbraiding.model.Appointment;
import org.example.backendbraiding.repository.AppointmentRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerAppointmentManagementService {
    private static final ZoneId SALON_ZONE = ZoneId.of("America/Chicago");
    private static final int CHANGE_CUTOFF_HOURS = 72;
    private static final int MAX_SELF_SERVICE_CHANGES = 1;

    private final AppointmentManagementTokenService tokenService;
    private final AppointmentRepository appointmentRepository;
    private final AvailabilityService availabilityService;
    private final EntityManager entityManager;
    private final AppointmentEventService appointmentEventService;
    private final NotificationOutboxService notificationOutboxService;
    private final AppointmentNotificationTemplates notificationTemplates;

    @Transactional(readOnly = true)
    public ManagedAppointmentDTO get(String token) {
        return map(tokenService.requireValid(token));
    }

    @Transactional(readOnly = true)
    public List<AvailableSlotDTO> slots(String token, java.time.LocalDate date) {
        Appointment appointment = tokenService.requireValid(token);
        Rules rules = rules(appointment);
        if (!rules.canChange()) throw new IllegalStateException(rules.lockReason());
        if (appointment.getService() == null) throw new IllegalStateException("This appointment cannot be rescheduled online");
        return availabilityService.getAvailableSlots(date, SALON_ZONE.getId(), appointment.getService().getId(), null)
                .stream().filter(slot -> Boolean.TRUE.equals(slot.getIsAvailable())).toList();
    }

    @Transactional
    @CacheEvict(value = {"appointments", "availableSlots"}, allEntries = true)
    public ManagedAppointmentDTO reschedule(String token, CustomerRescheduleRequest request) {
        Appointment appointment = tokenService.requireValidForUpdate(token);
        LocalDateTime requested = request.getAppointmentDateTime();
        if (requested.equals(appointment.getAppointmentDateTime())
                && safeCount(appointment) >= MAX_SELF_SERVICE_CHANGES) return map(appointment);
        requireChangeAllowed(appointment);
        if (requested.equals(appointment.getAppointmentDateTime())) {
            throw new IllegalArgumentException("Choose a different appointment time");
        }
        lockSlot(requested);
        List<AvailableSlotDTO> slots = availabilityService.getAvailableSlots(
                requested.toLocalDate(), SALON_ZONE.getId(),
                appointment.getService() == null ? null : appointment.getService().getId(), null);
        boolean available = slots.stream().anyMatch(slot -> requested.equals(slot.getStartTime())
                && Boolean.TRUE.equals(slot.getIsAvailable()));
        if (!available) throw new IllegalStateException("That appointment time is no longer available");

        long occupiedMinutes = appointment.getAppointmentEndDateTime() == null
                ? Math.max(1, appointment.getDurationMinutes() == null ? 60 : appointment.getDurationMinutes())
                : Math.max(1, Duration.between(appointment.getAppointmentDateTime(),
                appointment.getAppointmentEndDateTime()).toMinutes());
        LocalDateTime oldTime = appointment.getAppointmentDateTime();
        appointment.setRescheduledFromDateTime(oldTime);
        appointment.setAppointmentDateTime(requested);
        appointment.setAppointmentEndDateTime(requested.plusMinutes(occupiedMinutes));
        appointment.setSelfServiceChangeCount(safeCount(appointment) + 1);
        appointment.setLastSelfServiceChangeAt(salonNow());
        appointment.setManagementTokenExpiresAt(appointment.getAppointmentEndDateTime().plusDays(1));
        Appointment saved = appointmentRepository.save(appointment);
        appointmentEventService.record(saved, "CUSTOMER_RESCHEDULED", null,
                "Rescheduled from " + oldTime + " to " + requested);
        enqueueBoth(saved, notificationTemplates.customerRescheduled(saved));
        return map(saved);
    }

    @Transactional
    @CacheEvict(value = {"appointments", "availableSlots"}, allEntries = true)
    public ManagedAppointmentDTO cancel(String token, CustomerCancelRequest request) {
        Appointment appointment = tokenService.requireValidForUpdate(token);
        if (appointment.getStatus() == Appointment.AppointmentStatus.CANCELLED
                && Boolean.TRUE.equals(appointment.getCancelledByCustomer())) return map(appointment);
        requireChangeAllowed(appointment);
        String reason = request == null || request.getReason() == null || request.getReason().isBlank()
                ? "Cancelled by customer" : "Cancelled by customer: " + request.getReason().trim();
        appointment.setStatus(Appointment.AppointmentStatus.CANCELLED);
        appointment.setCancelledByCustomer(true);
        appointment.setSelfServiceChangeCount(safeCount(appointment) + 1);
        appointment.setLastSelfServiceChangeAt(salonNow());
        appointment.setAdminNotes(reason);
        Appointment saved = appointmentRepository.save(appointment);
        appointmentEventService.record(saved, "CUSTOMER_CANCELLED", null, reason);
        enqueueBoth(saved, notificationTemplates.customerCancelled(saved));
        return map(saved);
    }

    private void requireChangeAllowed(Appointment appointment) {
        Rules rules = rules(appointment);
        if (!rules.canChange()) throw new IllegalStateException(rules.lockReason());
    }

    private Rules rules(Appointment appointment) {
        if (appointment.getStatus() == Appointment.AppointmentStatus.CANCELLED) {
            return new Rules(false, "This appointment has been cancelled");
        }
        if (appointment.getStatus() != Appointment.AppointmentStatus.APPROVED) {
            return new Rules(false, "Only a confirmed appointment can be changed online");
        }
        if (safeCount(appointment) >= MAX_SELF_SERVICE_CHANGES) {
            return new Rules(false, "Your one self-service change has already been used");
        }
        if (!salonNow().isBefore(appointment.getAppointmentDateTime().minusHours(CHANGE_CUTOFF_HOURS))) {
            return new Rules(false, "The 72-hour cancellation and rescheduling deadline has passed");
        }
        return new Rules(true, null);
    }

    private ManagedAppointmentDTO map(Appointment appointment) {
        Rules rules = rules(appointment);
        return new ManagedAppointmentDTO(appointment.getId(), appointment.getCustomer().getFirstName(),
                maskEmail(appointment.getCustomer().getEmail()),
                appointment.getSelectedService() == null && appointment.getService() != null
                        ? appointment.getService().getName() : appointment.getSelectedService(),
                appointment.getSelectedSize(), appointment.getSelectedLength(), appointment.getSelectedFoundation(),
                appointment.getAppointmentDateTime(), appointment.getAppointmentEndDateTime(), appointment.getStatus().name(),
                appointment.getAmountCaptured() == null ? appointment.getDepositAmount() : appointment.getAmountCaptured(),
                appointment.getAppointmentDateTime().minusHours(CHANGE_CUTOFF_HOURS),
                Math.max(0, MAX_SELF_SERVICE_CHANGES - safeCount(appointment)), rules.canChange(), rules.canChange(),
                rules.lockReason());
    }

    private void lockSlot(LocalDateTime dateTime) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(?1, 0))")
                .setParameter(1, dateTime.toString()).getSingleResult();
    }

    private void enqueueBoth(Appointment appointment, AppointmentNotificationTemplates.Notification notification) {
        notificationOutboxService.enqueueEmail(appointment, notification.subject(), notification.emailBody());
        notificationOutboxService.enqueueSms(appointment, notification.smsBody());
    }

    private int safeCount(Appointment appointment) {
        return appointment.getSelfServiceChangeCount() == null ? 0 : appointment.getSelfServiceChangeCount();
    }
    private LocalDateTime salonNow() { return LocalDateTime.now(SALON_ZONE); }
    private String maskEmail(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at <= 1) return "••••";
        return email.substring(0, 1) + "••••••" + email.substring(at);
    }
    private record Rules(boolean canChange, String lockReason) {}
}
