package org.example.backendbraiding.service;

import com.vonage.client.VonageClient;
import com.vonage.client.sms.MessageStatus;
import com.vonage.client.sms.SmsSubmissionResponse;
import com.vonage.client.sms.messages.TextMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SmsService {

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
            
            System.out.println("=== SMS DEBUG ===");
            System.out.println("From (sender): " + fromPhoneNumber);
            System.out.println("To (recipient): " + formattedPhone);
            System.out.println("Message: " + messageBody);
            
            TextMessage message = new TextMessage(fromPhoneNumber, formattedPhone, messageBody);
            SmsSubmissionResponse response = getVonageClient().getSmsClient().submitMessage(message);
            
            if (response.getMessages().get(0).getStatus() == MessageStatus.OK) {
                System.out.println("SMS sent successfully to " + formattedPhone + ". Message ID: " + response.getMessages().get(0).getId());
            } else {
                System.err.println("SMS failed: " + response.getMessages().get(0).getErrorText());
            }
        } catch (Exception e) {
            System.err.println("Failed to send SMS to " + toPhoneNumber + ": " + e.getMessage());
            e.printStackTrace();
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
