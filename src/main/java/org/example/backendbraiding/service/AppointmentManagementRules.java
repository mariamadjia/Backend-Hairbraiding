package org.example.backendbraiding.service;

import org.example.backendbraiding.model.Appointment;

import java.time.LocalDateTime;

final class AppointmentManagementRules {
    private AppointmentManagementRules() {}

    static boolean slotFitsWindow(LocalDateTime start, int occupiedMinutes, LocalDateTime windowEnd) {
        return occupiedMinutes > 0 && !start.plusMinutes(occupiedMinutes).isAfter(windowEnd);
    }

    static boolean isActiveUpcoming(Appointment appointment, LocalDateTime now) {
        if (appointment.getAppointmentDateTime() == null || appointment.getAppointmentDateTime().isBefore(now)) return false;
        return appointment.getStatus() == Appointment.AppointmentStatus.APPROVED
                || (appointment.getStatus() == Appointment.AppointmentStatus.PENDING
                && appointment.getApprovedAt() != null);
    }

    static boolean isValidDateRange(LocalDateTime start, LocalDateTime end) {
        return end.isAfter(start) && !end.isAfter(start.plusDays(366));
    }
}
