package org.example.backendbraiding.service;

import org.example.backendbraiding.dto.PaymentIntentRequest;
import org.example.backendbraiding.model.Appointment;
import org.example.backendbraiding.model.Customer;
import org.example.backendbraiding.repository.AppointmentRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ZeroDepositPaymentTests {

    @Test
    void zeroDepositSkipsStripeAndWaitsForApprovalWithoutDuplicateNotifications() {
        AppointmentRepository appointments = mock(AppointmentRepository.class);
        BookingPaymentTokenService paymentTokens = mock(BookingPaymentTokenService.class);
        NotificationOutboxService outbox = mock(NotificationOutboxService.class);
        AppointmentEventService events = mock(AppointmentEventService.class);
        AppointmentNotificationTemplates templates = mock(AppointmentNotificationTemplates.class);
        AppointmentManagementTokenService managementTokens = mock(AppointmentManagementTokenService.class);
        Appointment appointment = appointment(true);
        var notification = new AppointmentNotificationTemplates.Notification("subject", "email", "sms");

        when(paymentTokens.isValidForAppointment("token", 1L)).thenReturn(true);
        when(appointments.findByIdForUpdate(1L)).thenReturn(Optional.of(appointment));
        when(appointments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(templates.pendingWithoutDeposit(appointment)).thenReturn(notification);
        when(templates.adminNewBooking(appointment)).thenReturn(notification);

        PaymentService service = new PaymentService(appointments, paymentTokens, outbox, events,
                templates, managementTokens, mock(OwnerDepositTokenService.class));
        var first = service.createPaymentIntent(new PaymentIntentRequest(1L, "token"));
        var second = service.createPaymentIntent(new PaymentIntentRequest(1L, "token"));

        assertNull(first.getClientSecret());
        assertEquals(0L, first.getAmount());
        assertEquals("NOT_REQUIRED", first.getPaymentStatus());
        assertEquals(Appointment.AppointmentStatus.PENDING, appointment.getStatus());
        assertEquals("NOT_REQUIRED", second.getPaymentStatus());
        verify(outbox, times(1)).enqueueCustomerAndSalon(eq(appointment), any(), any(), any(), any(), any(), any());
        verify(events, times(1)).record(appointment, "PAYMENT_NOT_REQUIRED", null, null);
    }

    @Test
    void zeroDepositAutoConfirmsWhenApprovalIsDisabled() {
        AppointmentRepository appointments = mock(AppointmentRepository.class);
        BookingPaymentTokenService paymentTokens = mock(BookingPaymentTokenService.class);
        NotificationOutboxService outbox = mock(NotificationOutboxService.class);
        AppointmentEventService events = mock(AppointmentEventService.class);
        AppointmentNotificationTemplates templates = mock(AppointmentNotificationTemplates.class);
        AppointmentManagementTokenService managementTokens = mock(AppointmentManagementTokenService.class);
        Appointment appointment = appointment(false);
        var notification = new AppointmentNotificationTemplates.Notification("subject", "email", "sms");

        when(paymentTokens.isValidForAppointment("token", 1L)).thenReturn(true);
        when(appointments.findByIdForUpdate(1L)).thenReturn(Optional.of(appointment));
        when(appointments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(managementTokens.issue(appointment)).thenReturn("manage-token");
        when(templates.approvedWithoutDeposit(appointment, "manage-token")).thenReturn(notification);
        when(templates.adminNewBooking(appointment)).thenReturn(notification);

        PaymentService service = new PaymentService(appointments, paymentTokens, outbox, events,
                templates, managementTokens, mock(OwnerDepositTokenService.class));
        var response = service.createPaymentIntent(new PaymentIntentRequest(1L, "token"));

        assertEquals("APPROVED", response.getAppointmentStatus());
        assertEquals("NOT_REQUIRED", response.getPaymentStatus());
        assertNotNull(appointment.getApprovedAt());
        verify(events).record(appointment, "APPROVED", null, "No deposit required");
    }

    private static Appointment appointment(boolean requireApproval) {
        Customer customer = new Customer();
        customer.setId(2L);
        customer.setFirstName("Test");
        customer.setLastName("Customer");
        customer.setEmail("test@example.com");
        customer.setPhoneNumber("+13185550100");

        Appointment appointment = new Appointment();
        appointment.setId(1L);
        appointment.setCustomer(customer);
        appointment.setStatus(Appointment.AppointmentStatus.PENDING);
        appointment.setPaymentStatus(Appointment.PaymentStatus.PENDING);
        appointment.setPaymentPendingExpiresAt(LocalDateTime.now().plusMinutes(10));
        appointment.setDepositAmount(0L);
        appointment.setPrice("100.00");
        appointment.setRequireApproval(requireApproval);
        return appointment;
    }
}
