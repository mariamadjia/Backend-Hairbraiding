package org.example.backendbraiding.service;

import com.vonage.client.VonageClient;
import com.vonage.client.sms.MessageStatus;
import com.vonage.client.sms.SmsSubmissionResponse;
import com.vonage.client.sms.messages.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SmsService {
    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    @Value("${vonage.api.key}")
    private String apiKey;

    @Value("${vonage.api.secret}")
    private String apiSecret;

    @Value("${vonage.phone.number}")
    private String fromPhoneNumber;

    private VonageClient vonageClient;

    private VonageClient getVonageClient() {
        if (vonageClient == null) {
            vonageClient = VonageClient.builder()
                    .apiKey(apiKey)
                    .apiSecret(apiSecret)
                    .build();
        }
        return vonageClient;
    }

    public void sendSms(String toPhoneNumber, String messageBody) {
        try {
            // Format phone number to E.164 format (add + if missing)
            String formattedPhone = toPhoneNumber;
            if (!formattedPhone.startsWith("+")) {
                formattedPhone = "+" + formattedPhone;
            }
            
            log.debug("SMS sending: from={} to={} message={}", fromPhoneNumber, formattedPhone, messageBody);
            
            TextMessage message = new TextMessage(fromPhoneNumber, formattedPhone, messageBody);
            SmsSubmissionResponse response = getVonageClient().getSmsClient().submitMessage(message);
            
            if (response.getMessages().get(0).getStatus() == MessageStatus.OK) {
                log.info("SMS sent successfully to {}. Message ID: {}", formattedPhone, response.getMessages().get(0).getId());
            } else {
                log.error("SMS failed: {}", response.getMessages().get(0).getErrorText());
            }
        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", toPhoneNumber, e.getMessage(), e);
        }
    }

    public void sendAppointmentApprovedSms(String customerPhone, String customerName, String appointmentDateTime) {
        String message = String.format(
            "Hi %s! Your braiding appointment for %s has been approved. We look forward to seeing you!",
            customerName,
            appointmentDateTime
        );
        sendSms(customerPhone, message);
    }

    public void sendAppointmentDeniedSms(String customerPhone, String customerName, String reason) {
        String message = String.format(
            "Hi %s, unfortunately we cannot accommodate your appointment request. Reason: %s. Please contact us to reschedule.",
            customerName,
            reason != null && !reason.isEmpty() ? reason : "Schedule conflict"
        );
        sendSms(customerPhone, message);
    }
}
