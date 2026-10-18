package org.example.backendbraiding.service;

import org.example.backendbraiding.dto.CheckoutRefundRequest;
import org.example.backendbraiding.dto.ExternalCheckoutPaymentRequest;
import org.example.backendbraiding.model.Admin;
import org.example.backendbraiding.model.Appointment;
import org.example.backendbraiding.model.AppointmentCheckout;
import org.example.backendbraiding.model.CheckoutTransaction;
import org.example.backendbraiding.model.Customer;
import org.example.backendbraiding.repository.AdminRepository;
import org.example.backendbraiding.repository.AppointmentCheckoutRepository;
import org.example.backendbraiding.repository.AppointmentRepository;
import org.example.backendbraiding.repository.CheckoutTransactionRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CheckoutServiceTests {
    private final AppointmentRepository appointments = mock(AppointmentRepository.class);
    private final AppointmentCheckoutRepository checkouts = mock(AppointmentCheckoutRepository.class);
    private final CheckoutTransactionRepository transactions = mock(CheckoutTransactionRepository.class);
    private final AdminRepository admins = mock(AdminRepository.class);
    private final AppointmentEventService events = mock(AppointmentEventService.class);
    private final NotificationOutboxService outbox = mock(NotificationOutboxService.class);
    private final CheckoutReceiptTemplates templates = mock(CheckoutReceiptTemplates.class);
    private final CheckoutService service = new CheckoutService(appointments, checkouts, transactions, admins, events, outbox, templates);

    @Test
    void externalPaymentCreditsDepositAndCompletesAppointment() {
        Appointment appointment = appointment();
        Admin admin = admin();
        when(appointments.findByIdForUpdate(37L)).thenReturn(Optional.of(appointment));
        when(admins.findById(4L)).thenReturn(Optional.of(admin));
        when(checkouts.findByAppointmentIdForUpdate(37L)).thenReturn(Optional.empty());
        when(checkouts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactions.findByCheckoutIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(templates.checkout(any(), any(), any())).thenReturn(new CheckoutReceiptTemplates.Receipt("Receipt", "Email", "SMS"));

        var result = service.recordExternalPayment(37L, 4L,
                new ExternalCheckoutPaymentRequest("CASH", 2_000L, "drawer-12", "Paid at salon"));

        assertEquals(Appointment.AppointmentStatus.COMPLETED, appointment.getStatus());
        assertEquals(18_000L, result.serviceTotalCents());
        assertEquals(5_000L, result.depositCreditCents());
        assertEquals(15_000L, result.amountPaidCents());
        assertEquals(0L, result.balanceDueCents());
        assertEquals("PAID", result.checkoutStatus());
        verify(outbox).enqueueSelected(eq(appointment), eq("Receipt"), eq("Email"), eq("SMS"), eq(true), eq(true));
        verify(events).record(eq(appointment), eq("CHECKOUT_COMPLETED"), eq(admin), contains("CASH"));
    }

    @Test
    void refundCannotExceedFinalCheckoutPayment() {
        Appointment appointment = appointment();
        appointment.setStatus(Appointment.AppointmentStatus.COMPLETED);
        AppointmentCheckout checkout = new AppointmentCheckout();
        checkout.setId(9L);
        checkout.setAppointment(appointment);
        checkout.setServiceTotalCents(18_000L);
        checkout.setDepositCreditCents(5_000L);
        checkout.setAmountPaidCents(13_000L);
        checkout.setAmountRefundedCents(2_000L);
        checkout.setStatus(AppointmentCheckout.Status.PARTIALLY_REFUNDED);
        when(appointments.findByIdForUpdate(37L)).thenReturn(Optional.of(appointment));
        when(checkouts.findByAppointmentIdForUpdate(37L)).thenReturn(Optional.of(checkout));
        when(admins.findById(4L)).thenReturn(Optional.of(admin()));

        var error = assertThrows(IllegalArgumentException.class, () -> service.recordExternalRefund(37L, 4L,
                new CheckoutRefundRequest(11_001L, "Customer request", null)));

        assertTrue(error.getMessage().contains("unrefunded"));
        verifyNoInteractions(transactions);
    }

    private Appointment appointment() {
        Customer customer = new Customer();
        customer.setId(30L);
        customer.setFirstName("Mikayla");
        customer.setLastName("Goodwine");
        customer.setEmail("customer@example.com");
        customer.setPhoneNumber("2105550100");
        Appointment appointment = new Appointment();
        appointment.setId(37L);
        appointment.setCustomer(customer);
        appointment.setSelectedService("Bohemian Box Braids");
        appointment.setPrice("180.00");
        appointment.setAppointmentDateTime(LocalDateTime.now().minusHours(5));
        appointment.setStatus(Appointment.AppointmentStatus.APPROVED);
        appointment.setPaymentStatus(Appointment.PaymentStatus.CAPTURED);
        appointment.setDepositAmount(5_000L);
        appointment.setAmountCaptured(5_000L);
        return appointment;
    }

    private Admin admin() {
        Admin admin = new Admin();
        admin.setId(4L);
        admin.setStatus("ACTIVE");
        return admin;
    }
}
