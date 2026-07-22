package org.example.backendbraiding.service;

import org.example.backendbraiding.model.Appointment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

final class CustomerAnalytics {
    private CustomerAnalytics() {}

    static BigDecimal capturedAmount(Appointment appointment) {
        if (appointment.getPaymentStatus() != Appointment.PaymentStatus.CAPTURED || appointment.getDepositAmount() == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(appointment.getDepositAmount(), 2);
    }

    static BigDecimal capturedTotal(List<Appointment> appointments) {
        return appointments.stream().map(CustomerAnalytics::capturedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static boolean isVisit(Appointment appointment) {
        return appointment.getStatus() == Appointment.AppointmentStatus.APPROVED
                || appointment.getStatus() == Appointment.AppointmentStatus.COMPLETED;
    }

    static boolean isUpcoming(Appointment appointment, LocalDateTime now) {
        return appointment.getAppointmentDateTime().isAfter(now)
                && (appointment.getStatus() == Appointment.AppointmentStatus.PENDING
                || appointment.getStatus() == Appointment.AppointmentStatus.APPROVED);
    }

    static String serviceName(Appointment appointment) {
        if (appointment.getSelectedService() != null && !appointment.getSelectedService().isBlank()) {
            return appointment.getSelectedService();
        }
        if (appointment.getService() != null && appointment.getService().getName() != null) {
            return appointment.getService().getName();
        }
        return "Unknown service";
    }
}
