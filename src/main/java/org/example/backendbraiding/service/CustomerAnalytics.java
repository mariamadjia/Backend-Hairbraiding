package org.example.backendbraiding.service;

import org.example.backendbraiding.model.Appointment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

final class CustomerAnalytics {
    private CustomerAnalytics() {}

    static BigDecimal capturedAmount(Appointment appointment) {
        if (appointment.getPaymentStatus() != Appointment.PaymentStatus.CAPTURED) {
            return BigDecimal.ZERO;
        }
        Long captured = appointment.getAmountCaptured() != null
                ? appointment.getAmountCaptured() : appointment.getDepositAmount();
        return captured == null ? BigDecimal.ZERO : BigDecimal.valueOf(captured, 2);
    }

    static BigDecimal capturedTotal(List<Appointment> appointments) {
        return appointments.stream().map(CustomerAnalytics::capturedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static boolean isVisit(Appointment appointment) {
        return appointment.getStatus() == Appointment.AppointmentStatus.COMPLETED;
    }

    static boolean isUpcoming(Appointment appointment, LocalDateTime now) {
        return appointment.getAppointmentDateTime().isAfter(now)
                && (appointment.getStatus() == Appointment.AppointmentStatus.PENDING
                || appointment.getStatus() == Appointment.AppointmentStatus.APPROVED);
    }

    static String serviceName(Appointment appointment) {
        if (appointment.getSelectedService() != null && !appointment.getSelectedService().isBlank()
                && !appointment.getSelectedService().equalsIgnoreCase(nullToEmpty(appointment.getSelectedSize()))) {
            return appointment.getSelectedService();
        }
        if (appointment.getService() != null && appointment.getService().getSubcategory() != null
                && appointment.getService().getSubcategory().getName() != null
                && !appointment.getService().getSubcategory().getName().isBlank()) {
            return appointment.getService().getSubcategory().getName().trim();
        }
        if (appointment.getService() != null && appointment.getService().getName() != null) {
            return appointment.getService().getName();
        }
        return "Unknown service";
    }

    private static String nullToEmpty(String value) { return value == null ? "" : value; }
}
