package org.example.backendbraiding.service;

import org.example.backendbraiding.model.Appointment;
import org.example.backendbraiding.model.AppointmentAddOn;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class AppointmentNotificationTemplates {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern(
            "EEEE, MMMM d, yyyy 'at' h:mm a", Locale.US);

    private final String salonName;
    private final String phone;
    private final String email;
    private final String addressLine1;
    private final String addressLine2;
    private final String hoursWeekday;
    private final String hoursSunday;
    private final String website;

    public AppointmentNotificationTemplates(
            @Value("${salon.name:AH Braiding Salon}") String salonName,
            @Value("${salon.phone:(210) 812-8121}") String phone,
            @Value("${salon.email:adjiashairbraiding@gmail.com}") String email,
            @Value("${salon.address-line-1:1305 SW Loop 410, Unit 203}") String addressLine1,
            @Value("${salon.address-line-2:San Antonio, TX 78227}") String addressLine2,
            @Value("${salon.hours.weekday:Monday-Saturday: 9:00 AM-7:00 PM}") String hoursWeekday,
            @Value("${salon.hours.sunday:Sunday: 10:00 AM–5:00 PM}") String hoursSunday,
            @Value("${salon.website:}") String website) {
        this.salonName = salonName;
        this.phone = phone;
        this.email = email;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.hoursWeekday = normalizeHours(hoursWeekday);
        this.hoursSunday = normalizeHours(hoursSunday);
        this.website = website == null ? "" : website.trim();
    }

    public Notification approved(Appointment appointment) {
        String body = greeting(appointment)
                + "Your appointment with " + salonName + " is confirmed.\n\n"
                + summary(appointment, "Deposit charged")
                + "Your deposit is non-refundable under the policy accepted when booking. Please arrive on time and contact us if you have any questions before your appointment.\n\n"
                + footer();
        return new Notification("Your appointment is confirmed — " + shortDate(appointment), body,
                "Hi " + firstName(appointment) + ", your " + salonName + " appointment is confirmed for "
                        + dateTime(appointment) + " CT. Deposit charged: " + money(capturedAmount(appointment))
                        + ". Questions? " + phone);
    }

    public Notification denied(Appointment appointment) {
        String body = greeting(appointment)
                + "Unfortunately, we’re unable to accommodate your appointment request for " + dateTime(appointment) + ".\n\n"
                + "Reason: " + reason(appointment) + "\n\n"
                + authorizationReleaseText(appointment)
                + "We’d be happy to help you select another available time.\n\n"
                + footer();
        return new Notification("Update on your " + salonName + " appointment request", body,
                "Hi " + firstName(appointment) + ", we’re unable to accommodate your appointment request for "
                        + dateTime(appointment) + " CT. Reason: " + reason(appointment) + " "
                        + shortPaymentOutcome(appointment) + " Call/text " + phone + " to reschedule.");
    }

    public Notification cancelled(Appointment appointment) {
        String body = greeting(appointment)
                + "Your appointment for " + dateTime(appointment) + " has been cancelled.\n\n"
                + "Reason: " + reason(appointment) + "\n\n"
                + cancellationPaymentText(appointment)
                + "Please contact us if you have questions or would like to book another available time.\n\n"
                + footer();
        return new Notification("Your " + salonName + " appointment has been cancelled", body,
                "Hi " + firstName(appointment) + ", your appointment for " + dateTime(appointment)
                        + " CT was cancelled. Reason: " + reason(appointment) + " "
                        + shortPaymentOutcome(appointment) + " Questions? " + phone);
    }

    private String greeting(Appointment appointment) {
        return "Hi " + firstName(appointment) + ",\n\n";
    }

    private String summary(Appointment appointment, String depositLabel) {
        StringBuilder value = new StringBuilder("APPOINTMENT DETAILS\n")
                .append("Date and time: ").append(dateTime(appointment)).append(" CT\n")
                .append("Service: ").append(serviceName(appointment)).append("\n");
        append(value, "Size", appointment.getSelectedSize());
        append(value, "Length", appointment.getSelectedLength());
        append(value, "Foundation", friendlyFoundation(appointment.getSelectedFoundation()));
        append(value, "Texture", appointment.getSelectedTexture());
        if (appointment.getAddOns() != null && !appointment.getAddOns().isEmpty()) {
            value.append("Add-ons: ").append(appointment.getAddOns().stream()
                    .map(AppointmentAddOn::getAddOnName).collect(Collectors.joining(", "))).append("\n");
        }
        value.append(depositLabel).append(": ").append(money(depositAmount(appointment))).append("\n\n");
        return value.toString();
    }

    private void append(StringBuilder value, String label, String field) {
        if (field != null && !field.isBlank()) value.append(label).append(": ").append(field).append("\n");
    }

    private String footer() {
        StringBuilder value = new StringBuilder("Questions about your appointment? Reply to this email or call/text us at ")
                .append(phone).append(".\n\n")
                .append("Warmly,\n").append(salonName).append("\n")
                .append(addressLine1).append("\n").append(addressLine2).append("\n")
                .append("Phone: ").append(phone).append("\n")
                .append(hoursWeekday).append("\n").append(hoursSunday);
        if (!website.isBlank()) value.append("\nWebsite: ").append(website);
        return value.toString();
    }

    private String normalizeHours(String value) {
        if (value == null) return "";
        return value.replace("â€“", "-")
                .replace("–", "-")
                .replace("—", "-");
    }

    private String authorizationReleaseText(Appointment appointment) {
        return switch (paymentStatus(appointment)) {
            case CAPTURED -> "The deposit was already captured and remains non-refundable under the policy accepted when booking.\n\n";
            case AUTHORIZED, CANCELLATION_FAILED -> "We are releasing the payment authorization; your deposit was not charged. Your bank may take a few business days to remove a pending hold.\n\n";
            default -> "No deposit was charged for this request.\n\n";
        };
    }

    private String cancellationPaymentText(Appointment appointment) {
        return switch (paymentStatus(appointment)) {
            case CAPTURED -> "As stated in the policy accepted during booking, the captured deposit of "
                    + money(capturedAmount(appointment)) + " is non-refundable.\n\n";
            case AUTHORIZED, CANCELLATION_FAILED -> "We are releasing the payment authorization; your deposit was not charged. Your bank may take a few business days to remove a pending hold.\n\n";
            default -> "No deposit was charged for this appointment.\n\n";
        };
    }

    private String shortPaymentOutcome(Appointment appointment) {
        return switch (paymentStatus(appointment)) {
            case CAPTURED -> "The captured deposit is non-refundable.";
            case AUTHORIZED, CANCELLATION_FAILED -> "The authorization is being released; your deposit was not charged.";
            default -> "No deposit was charged.";
        };
    }

    private Appointment.PaymentStatus paymentStatus(Appointment appointment) {
        return appointment.getPaymentStatus() == null ? Appointment.PaymentStatus.PENDING : appointment.getPaymentStatus();
    }

    private long depositAmount(Appointment appointment) {
        return appointment.getDepositAmount() == null ? 0 : appointment.getDepositAmount();
    }

    private long capturedAmount(Appointment appointment) {
        return appointment.getAmountCaptured() == null ? depositAmount(appointment) : appointment.getAmountCaptured();
    }

    private String money(long cents) {
        return "$" + BigDecimal.valueOf(cents, 2).setScale(2).toPlainString();
    }

    private String dateTime(Appointment appointment) {
        return appointment.getAppointmentDateTime().format(DATE_TIME);
    }

    private String shortDate(Appointment appointment) {
        return appointment.getAppointmentDateTime().format(DateTimeFormatter.ofPattern("MMMM d", Locale.US));
    }

    private String firstName(Appointment appointment) {
        String name = appointment.getCustomer().getFirstName();
        return name == null || name.isBlank() ? "there" : name.trim();
    }

    private String serviceName(Appointment appointment) {
        if (appointment.getSelectedService() != null && !appointment.getSelectedService().isBlank()
                && !appointment.getSelectedService().equalsIgnoreCase(nullToEmpty(appointment.getSelectedSize()))) {
            return appointment.getSelectedService();
        }
        if (appointment.getService() != null && appointment.getService().getSubcategory() != null
                && appointment.getService().getSubcategory().getName() != null
                && !appointment.getService().getSubcategory().getName().isBlank()) {
            return appointment.getService().getSubcategory().getName().trim();
        }
        if (appointment.getSelectedService() != null && !appointment.getSelectedService().isBlank()) return appointment.getSelectedService();
        if (appointment.getService() != null && appointment.getService().getName() != null) return appointment.getService().getName();
        return "Braiding appointment";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String friendlyFoundation(String foundation) {
        if (foundation == null || foundation.isBlank()) return null;
        return "KNOTLESS".equalsIgnoreCase(foundation) ? "Knotless" : "Regular";
    }

    private String reason(Appointment appointment) {
        return appointment.getAdminNotes() == null || appointment.getAdminNotes().isBlank()
                ? "Scheduling availability" : appointment.getAdminNotes().trim();
    }

    public record Notification(String subject, String emailBody, String smsBody) {}
}
