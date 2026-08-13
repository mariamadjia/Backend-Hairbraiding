package org.example.backendbraiding.service;

import org.example.backendbraiding.model.Appointment;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppointmentManagementRulesTests {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 12, 10, 0);

    @Test
    void slotAndBufferMustFitBeforeClosing() {
        LocalDateTime close = NOW.plusHours(1);
        assertTrue(AppointmentManagementRules.slotFitsWindow(NOW, 60, close));
        assertFalse(AppointmentManagementRules.slotFitsWindow(NOW, 61, close));
        assertFalse(AppointmentManagementRules.slotFitsWindow(NOW, 0, close));
    }

    @Test
    void upcomingExcludesClosedAndUnsubmittedAppointments() {
        assertTrue(AppointmentManagementRules.isActiveUpcoming(appointment(
                Appointment.AppointmentStatus.APPROVED, null, NOW.plusHours(1)), NOW));
        assertTrue(AppointmentManagementRules.isActiveUpcoming(appointment(
                Appointment.AppointmentStatus.PENDING, NOW, NOW.plusHours(1)), NOW));
        assertFalse(AppointmentManagementRules.isActiveUpcoming(appointment(
                Appointment.AppointmentStatus.PENDING, null, NOW.plusHours(1)), NOW));
        assertFalse(AppointmentManagementRules.isActiveUpcoming(appointment(
                Appointment.AppointmentStatus.CANCELLED, NOW, NOW.plusHours(1)), NOW));
    }

    @Test
    void managementDateRangeIsOrderedAndBounded() {
        assertTrue(AppointmentManagementRules.isValidDateRange(NOW, NOW.plusDays(366)));
        assertFalse(AppointmentManagementRules.isValidDateRange(NOW, NOW));
        assertFalse(AppointmentManagementRules.isValidDateRange(NOW, NOW.plusDays(367)));
    }

    private Appointment appointment(Appointment.AppointmentStatus status, LocalDateTime approvedAt, LocalDateTime start) {
        Appointment appointment = new Appointment();
        appointment.setStatus(status);
        appointment.setApprovedAt(approvedAt);
        appointment.setAppointmentDateTime(start);
        return appointment;
    }
}
