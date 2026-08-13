package org.example.backendbraiding.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender mailSender;
    private final String frontendUrl;
    private final String salonEmail;

    public EmailService(JavaMailSender mailSender, @Value("${app.frontend-url}") String frontendUrl,
                        @Value("${salon.email:adjiashairbraiding@gmail.com}") String salonEmail) {
        this.mailSender = mailSender;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.salonEmail = salonEmail;
    }

    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        sendSecurityEmail(toEmail, "Reset your AH Braiding admin password",
                "A password reset was requested for your AH Braiding administrator account.\n\n" +
                "Create a new password within 30 minutes:\n" + frontendUrl + "/admin/reset-password?token=" + resetToken +
                "\n\nIf you did not request this, you can ignore this email.");
    }

    public void sendAdminInvitation(String toEmail, String firstName, String token) {
        sendSecurityEmail(toEmail, "You're invited to AH Braiding Admin",
                "Hello " + firstName + ",\n\nYou've been invited to manage AH Braiding. " +
                "Create your password within 24 hours:\n" + frontendUrl + "/admin/set-password?token=" + token +
                "\n\nIf you were not expecting this invitation, you can ignore this email.");
    }

    public void sendPasswordChangedEmail(String toEmail) {
        sendSecurityEmail(toEmail, "Your AH Braiding admin password was changed",
                "Your administrator password was changed successfully. If you did not make this change, contact the account owner immediately.");
    }

    private void sendSecurityEmail(String toEmail, String subject, String body) {
        try {
            mailSender.send(createUtf8Message(toEmail, subject, body));
            log.info("Security email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send security email to: {}", toEmail, e);
            throw new IllegalStateException("Email could not be sent. Please try again.");
        }
    }

    public boolean sendAppointmentUpdate(String toEmail, String subject, String body) {
        try {
            mailSender.send(createUtf8Message(toEmail, subject, body));
            return true;
        } catch (Exception e) {
            // Booking state must never roll back because a notification provider is unavailable.
            log.error("Failed to send appointment email to {}: {}", toEmail, e.getMessage());
            return false;
        }
    }

    private MimeMessage createUtf8Message(String toEmail, String subject, String body) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setTo(toEmail);
        helper.setReplyTo(salonEmail);
        helper.setSubject(subject);
        helper.setText(body, false);
        return message;
    }
}
