package org.example.backendbraiding.service;

import org.example.backendbraiding.model.Appointment;
import org.example.backendbraiding.model.Customer;
import org.example.backendbraiding.model.ServiceItem;
import org.example.backendbraiding.model.Subcategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppointmentNotificationTemplatesTests {
    private AppointmentNotificationTemplates templates;

    @BeforeEach
    void setUp() {
        templates = new AppointmentNotificationTemplates(
                "AH Braiding Salon", "(210) 812-8121", "adjiashairbraiding@gmail.com",
                "1305 SW Loop 410, Unit 203", "San Antonio, TX 78227",
                "Monday–Saturday: 9:00 AM–7:00 PM", "Sunday: 10:00 AM–5:00 PM", "");
    }

    @Test
    void bookingMessageFormatsCustomerDateMoneyAndSalonDetails() {
        var notification = templates.bookingCreated(appointment(Appointment.PaymentStatus.PENDING));

        assertTrue(notification.subject().contains("Complete your appointment request"));
        assertTrue(notification.emailBody().contains("Hi Gloria,"));
        assertTrue(notification.emailBody().contains("Thursday, August 20, 2026 at 2:00 PM CT"));
        assertTrue(notification.emailBody().contains("Deposit to authorize: $50.00"));
        assertTrue(notification.emailBody().contains("1305 SW Loop 410, Unit 203"));
        assertTrue(notification.emailBody().contains("(210) 812-8121"));
        assertFalse(notification.emailBody().contains("Website:"));
    }

    @Test
    void approvedMessageStatesCapturedNonRefundableDeposit() {
        Appointment appointment = appointment(Appointment.PaymentStatus.CAPTURED);
        appointment.setAmountCaptured(5000L);
        var notification = templates.approved(appointment);

        assertTrue(notification.emailBody().contains("Deposit charged: $50.00"));
        assertTrue(notification.emailBody().contains("deposit is non-refundable"));
        assertTrue(notification.smsBody().contains("Deposit charged: $50.00"));
    }

    @Test
    void deniedAuthorizationSaysCustomerWasNotCharged() {
        Appointment appointment = appointment(Appointment.PaymentStatus.AUTHORIZED);
        appointment.setAdminNotes("The requested time is unavailable");
        var notification = templates.denied(appointment);

        assertTrue(notification.emailBody().contains("deposit was not charged"));
        assertTrue(notification.emailBody().contains("few business days"));
        assertTrue(notification.emailBody().contains("The requested time is unavailable"));
    }

    @Test
    void cancellationCopyDependsOnPaymentState() {
        Appointment captured = appointment(Appointment.PaymentStatus.CAPTURED);
        captured.setAmountCaptured(5000L);
        assertTrue(templates.cancelled(captured).emailBody().contains("captured deposit of $50.00 is non-refundable"));

        Appointment authorized = appointment(Appointment.PaymentStatus.AUTHORIZED);
        assertTrue(templates.cancelled(authorized).emailBody().contains("deposit was not charged"));

        Appointment unpaid = appointment(Appointment.PaymentStatus.PENDING);
        assertTrue(templates.cancelled(unpaid).emailBody().contains("No deposit was charged"));
    }

    @Test
    void configuredWebsiteAppearsOnlyWhenAvailable() {
        AppointmentNotificationTemplates withWebsite = new AppointmentNotificationTemplates(
                "AH Braiding Salon", "(210) 812-8121", "adjiashairbraiding@gmail.com",
                "1305 SW Loop 410, Unit 203", "San Antonio, TX 78227",
                "Monday–Saturday: 9:00 AM–7:00 PM", "Sunday: 10:00 AM–5:00 PM",
                "https://example.com");
        assertTrue(withWebsite.approved(appointment(Appointment.PaymentStatus.CAPTURED))
                .emailBody().contains("Website: https://example.com"));
    }

    @Test
    void legacySizeSnapshotFallsBackToAuthoritativeStyleName() {
        Appointment appointment = appointment(Appointment.PaymentStatus.PENDING);
        appointment.setSelectedService("XSmall");
        appointment.setSelectedSize("XSmall");
        Subcategory style = new Subcategory();
        style.setName("Knotless Box Braids");
        ServiceItem size = new ServiceItem();
        size.setName("XSmall");
        size.setSubcategory(style);
        appointment.setService(size);

        String body = templates.bookingCreated(appointment).emailBody();
        assertTrue(body.contains("Service: Knotless Box Braids"));
        assertTrue(body.contains("Size: XSmall"));
        assertFalse(body.contains("Service: XSmall"));
    }

    private Appointment appointment(Appointment.PaymentStatus paymentStatus) {
        Customer customer = new Customer();
        customer.setFirstName("Gloria");
        customer.setLastName("Djonret");
        customer.setEmail("customer@example.com");
        customer.setPhoneNumber("2105550100");

        Appointment appointment = new Appointment();
        appointment.setCustomer(customer);
        appointment.setAppointmentDateTime(LocalDateTime.of(2026, 8, 20, 14, 0));
        appointment.setSelectedService("Knotless Box Braids");
        appointment.setSelectedLength("Mid-back");
        appointment.setDepositAmount(5000L);
        appointment.setPaymentStatus(paymentStatus);
        appointment.setAdminNotes("Schedule change");
        return appointment;
    }
}
