package org.example.backendbraiding.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import jakarta.annotation.PostConstruct;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender mailSender;
    private final String frontendUrl;
    private final String salonEmail;
    private final String salonName;
    private final String mailUsername;
    private final String mailPassword;
    private final boolean emailRequired;

    public EmailService(JavaMailSender mailSender, @Value("${app.frontend-url}") String frontendUrl,
                        @Value("${salon.email:adjiashairbraiding@gmail.com}") String salonEmail,
                        @Value("${salon.name:AH Braiding Salon}") String salonName,
                        @Value("${spring.mail.username:}") String mailUsername,
                        @Value("${spring.mail.password:}") String mailPassword,
                        @Value("${notifications.email.required:false}") boolean emailRequired) {
        this.mailSender = mailSender;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.salonEmail = salonEmail;
        this.salonName = salonName;
        this.mailUsername = mailUsername;
        this.mailPassword = mailPassword;
        this.emailRequired = emailRequired;
    }

    @PostConstruct
    void validateConfiguration() {
        if (!emailRequired) return;
        if (mailUsername.isBlank() || mailPassword.isBlank()
                || mailUsername.startsWith("your-") || mailPassword.startsWith("your-")) {
            throw new IllegalStateException("Production email delivery is required but SMTP credentials are not configured");
        }
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

    public boolean sendChatNotification(String customerName, String customerEmail, String customerPhone,
                                        String customerMessage, byte[] photoBytes, String photoFilename,
                                        String photoContentType) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            boolean hasPhoto = photoBytes != null && photoBytes.length > 0;
            MimeMessageHelper helper = new MimeMessageHelper(message, hasPhoto, "UTF-8");
            helper.setTo(salonEmail);
            helper.setFrom(mailUsername == null || mailUsername.isBlank() ? salonEmail : mailUsername, salonName);
            helper.setReplyTo(customerEmail);
            helper.setSubject("New website message from " + customerName);
            helper.setText("New message submitted through the AH Braiding website chat.\n\n" +
                    "Name: " + customerName + "\n" +
                    "Email: " + customerEmail + "\n" +
                    "Phone: " + customerPhone + "\n\n" +
                    "Message:\n" + customerMessage);
            if (hasPhoto) {
                String filename = photoFilename == null || photoFilename.isBlank() ? "style-photo" : photoFilename;
                helper.addAttachment(filename, new ByteArrayResource(photoBytes),
                        photoContentType == null ? "application/octet-stream" : photoContentType);
            }
            mailSender.send(message);
            log.info("Chat notification email sent to: {}", salonEmail);
            return true;
        } catch (Exception e) {
            log.error("Failed to send chat notification email to {}: {}", salonEmail, e.getMessage());
            return false;
        }
    }

    private MimeMessage createUtf8Message(String toEmail, String subject, String body) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setTo(toEmail);
        helper.setFrom(mailUsername == null || mailUsername.isBlank() ? salonEmail : mailUsername, salonName);
        helper.setReplyTo(salonEmail);
        helper.setSubject(subject);
        boolean html = body != null && body.stripLeading().startsWith("<!doctype html>");
        if (html) helper.setText(toPlainText(body), body);
        else helper.setText(body == null ? "" : body);
        return message;
    }

    private String toPlainText(String html) {
        return html.replaceAll("(?is)<style.*?</style>|<script.*?</script>", "")
                .replaceAll("(?i)<br\\s*/?>|</p>|</div>|</tr>|</h[1-6]>", "\n")
                .replaceAll("(?s)<[^>]+>", "")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
                .replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
    }
}
