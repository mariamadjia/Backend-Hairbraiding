package org.example.backendbraiding.service;

import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.model.Appointment;
import org.example.backendbraiding.repository.AppointmentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AppointmentManagementTokenService {
    private final AppointmentRepository appointmentRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    @Value("${app.frontend-url}")
    private String frontendUrl;

    public String issue(Appointment appointment) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        appointment.setManagementTokenHash(hash(token));
        LocalDateTime end = appointment.getAppointmentEndDateTime() == null
                ? appointment.getAppointmentDateTime() : appointment.getAppointmentEndDateTime();
        appointment.setManagementTokenExpiresAt(end.plusDays(1));
        appointmentRepository.save(appointment);
        return frontendUrl.replaceAll("/+$", "") + "/manage-appointment/" + token;
    }

    public Appointment requireValid(String token) {
        if (token == null || token.length() < 40 || token.length() > 100) {
            throw new IllegalArgumentException("Invalid appointment management link");
        }
        Appointment appointment = appointmentRepository.findByManagementTokenHash(hash(token))
                .orElseThrow(() -> new org.example.backendbraiding.exception.ResourceNotFoundException("Appointment management link not found"));
        if (appointment.getManagementTokenExpiresAt() == null
                || !appointment.getManagementTokenExpiresAt().isAfter(LocalDateTime.now(ZoneId.of("America/Chicago")))) {
            throw new IllegalStateException("Appointment management link has expired");
        }
        return appointment;
    }

    public Appointment requireValidForUpdate(String token) {
        if (token == null || token.length() < 40 || token.length() > 100) {
            throw new IllegalArgumentException("Invalid appointment management link");
        }
        Appointment appointment = appointmentRepository.findByManagementTokenHashForUpdate(hash(token))
                .orElseThrow(() -> new org.example.backendbraiding.exception.ResourceNotFoundException("Appointment management link not found"));
        if (appointment.getManagementTokenExpiresAt() == null
                || !appointment.getManagementTokenExpiresAt().isAfter(LocalDateTime.now(ZoneId.of("America/Chicago")))) {
            throw new IllegalStateException("Appointment management link has expired");
        }
        return appointment;
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
