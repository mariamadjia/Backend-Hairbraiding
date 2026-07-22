package org.example.backendbraiding.service;

import org.example.backendbraiding.model.Appointment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CustomerAnalyticsTests {
    @Test
    void onlyCapturedDepositsCountAsMoneyPaid() {
        Appointment captured = appointment(Appointment.AppointmentStatus.COMPLETED, Appointment.PaymentStatus.CAPTURED, 5000L, -1);
        Appointment authorized = appointment(Appointment.AppointmentStatus.PENDING, Appointment.PaymentStatus.AUTHORIZED, 5000L, 1);
        Appointment cancelled = appointment(Appointment.AppointmentStatus.CANCELLED, Appointment.PaymentStatus.CANCELLED, 5000L, -2);

        assertEquals(new BigDecimal("50.00"), CustomerAnalytics.capturedTotal(List.of(captured, authorized, cancelled)));
        assertEquals(BigDecimal.ZERO, CustomerAnalytics.capturedAmount(authorized));
    }

    @Test
    void visitAndUpcomingDefinitionsExcludeTerminalRequests() {
        LocalDateTime now = LocalDateTime.now();
        Appointment completed = appointment(Appointment.AppointmentStatus.COMPLETED, Appointment.PaymentStatus.CAPTURED, 5000L, -1);
        Appointment upcoming = appointment(Appointment.AppointmentStatus.APPROVED, Appointment.PaymentStatus.CAPTURED, 5000L, 1);
        Appointment denied = appointment(Appointment.AppointmentStatus.DENIED, Appointment.PaymentStatus.CANCELLED, null, 1);

        assertTrue(CustomerAnalytics.isVisit(completed));
        assertTrue(CustomerAnalytics.isUpcoming(upcoming, now));
        assertFalse(CustomerAnalytics.isUpcoming(denied, now));
    }

    @Test
    void serviceSnapshotSurvivesMissingServiceRelationship() {
        Appointment appointment = new Appointment();
        appointment.setSelectedService("Classic Box Braids");
        assertEquals("Classic Box Braids", CustomerAnalytics.serviceName(appointment));
    }

    private Appointment appointment(Appointment.AppointmentStatus status, Appointment.PaymentStatus paymentStatus,
                                    Long deposit, int daysFromNow) {
        Appointment appointment = new Appointment();
        appointment.setStatus(status);
        appointment.setPaymentStatus(paymentStatus);
        appointment.setDepositAmount(deposit);
        appointment.setAppointmentDateTime(LocalDateTime.now().plusDays(daysFromNow));
        return appointment;
    }
}
