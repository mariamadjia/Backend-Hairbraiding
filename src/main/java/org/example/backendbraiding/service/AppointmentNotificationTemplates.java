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
    private final String frontendUrl;

    public AppointmentNotificationTemplates(
            @Value("${salon.name:AH Braiding Salon}") String salonName,
            @Value("${salon.phone:(210) 812-8121}") String phone,
            @Value("${salon.email:adjiashairbraiding@gmail.com}") String email,
            @Value("${salon.address-line-1:1305 SW Loop 410, Unit 203}") String addressLine1,
            @Value("${salon.address-line-2:San Antonio, TX 78227}") String addressLine2,
            @Value("${salon.hours.weekday:Monday-Saturday: 9:00 AM-7:00 PM}") String hoursWeekday,
            @Value("${salon.hours.sunday:Sunday: 10:00 AM–5:00 PM}") String hoursSunday,
            @Value("${salon.website:}") String website,
            @Value("${app.frontend-url}") String frontendUrl) {
        this.salonName = salonName;
        this.phone = phone;
        this.email = email;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.hoursWeekday = normalizeHours(hoursWeekday);
        this.hoursSunday = normalizeHours(hoursSunday);
        this.website = website == null ? "" : website.trim();
        this.frontendUrl = frontendUrl == null ? "" : frontendUrl.replaceAll("/+$", "");
    }

    public Notification pending(Appointment appointment) {
        String body = brandedEmail(
                "Your appointment request is pending",
                "Thank you for submitting your appointment request to " + salonName + ".",
                "Your request has been received and is currently awaiting confirmation from our team. "
                        + "We’ll notify you by email as soon as your appointment is approved or if any changes are needed.",
                appointment,
                "Deposit authorized",
                "Your card has not been charged. The deposit will only be charged if your appointment is approved.",
                "Please do not submit another request while this appointment is under review.",
                null,
                null,
                null);
        return new Notification("Appointment request received — awaiting confirmation", body, "");
    }

    public Notification adminNewBooking(Appointment appointment) {
        StringBuilder body = new StringBuilder("A new appointment request has been submitted.\n\n")
                .append("Customer: ").append(customerName(appointment)).append("\n")
                .append("Email: ").append(appointment.getCustomer().getEmail()).append("\n")
                .append("Phone: ").append(appointment.getCustomer().getPhoneNumber()).append("\n\n")
                .append(summary(appointment, "Deposit authorized"))
                .append("Open Appointment Management to approve or deny this request.");
        return new Notification("New booking request — " + customerName(appointment), body.toString(), "");
    }

    public Notification approved(Appointment appointment) {
        return approved(appointment, frontendUrl + "/booking");
    }

    public Notification approved(Appointment appointment, String managementUrl) {
        String body = brandedEmail(
                "Your appointment is confirmed",
                "We’re excited to see you! Your appointment has been confirmed. Here are the details:",
                null,
                appointment,
                "Deposit charged",
                "Your deposit is non-refundable under the policy accepted when booking.",
                "Please arrive on time.",
                "Missed appointments will incur a 60% service fee.",
                "Manage Appointment",
                managementUrl);
        return new Notification("Your appointment is confirmed — " + shortDate(appointment), body,
                "Hi " + firstName(appointment) + ", your " + salonName + " appointment is confirmed for "
                        + dateTime(appointment) + " CT. Deposit charged: " + money(capturedAmount(appointment))
                        + ". Questions? " + phone);
    }

    public Notification customerRescheduled(Appointment appointment) {
        String body = brandedEmail(
                "Your appointment has been rescheduled",
                "Your one-time appointment change is confirmed.",
                "The appointment details below reflect your new date and time.",
                appointment,
                "Deposit charged",
                "Your deposit remains applied to this appointment and is non-refundable.",
                "Your one self-service appointment change has now been used.",
                null,
                null,
                null);
        return new Notification("Your appointment has been rescheduled", body,
                "Hi " + firstName(appointment) + ", your appointment is now " + dateTime(appointment)
                        + " CT. Your one self-service change has been used. Questions? " + phone);
    }

    public Notification adminCustomerRescheduled(Appointment appointment) {
        String body = "A customer rescheduled an appointment.\n\nCustomer: " + customerName(appointment)
                + "\nNew appointment: " + dateTime(appointment) + " CT"
                + "\nService: " + serviceName(appointment)
                + "\nPrevious appointment: " + (appointment.getRescheduledFromDateTime() == null
                ? "Not available" : DATE_TIME.format(appointment.getRescheduledFromDateTime()) + " CT")
                + "\n\nOpen Appointment Management to review the activity.";
        return new Notification("Customer rescheduled appointment — " + shortDate(appointment), body, "");
    }

    public Notification customerCancelled(Appointment appointment) {
        String body = brandedEmail(
                "Your appointment has been cancelled",
                "Your appointment has been cancelled as requested.",
                "This cancellation is final and your appointment time has been released.",
                appointment,
                cancellationDepositLabel(appointment),
                cancellationPaymentNotice(appointment),
                "You may submit a new request whenever you are ready to book another available time.",
                null,
                null,
                null);
        return new Notification("Your appointment has been cancelled", body,
                "Hi " + firstName(appointment) + ", your appointment for " + dateTime(appointment)
                        + " CT has been cancelled. " + shortPaymentOutcome(appointment));
    }

    public Notification adminCustomerCancelled(Appointment appointment) {
        String body = "A customer cancelled an appointment.\n\nCustomer: " + customerName(appointment)
                + "\nAppointment: " + dateTime(appointment) + " CT"
                + "\nService: " + serviceName(appointment)
                + "\nReason: " + (appointment.getCustomerCancellationReason() == null
                || appointment.getCustomerCancellationReason().isBlank() ? "No reason provided"
                : appointment.getCustomerCancellationReason())
                + "\n\nThe appointment time has been released.";
        return new Notification("Customer cancelled appointment — " + shortDate(appointment), body, "");
    }

    private String brandedEmail(String heading, String intro, String secondary, Appointment appointment,
                                String depositLabel, String notice, String closing,
                                String noShowPolicy, String buttonLabel, String buttonUrl) {
        String button = buttonLabel == null ? "" : """
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="margin:28px 0 24px">
                  <tr><td align="center"><a href="%s" style="display:inline-block;background:#632b14;color:#ffffff;text-decoration:none;font-family:Arial,sans-serif;font-size:17px;font-weight:600;padding:16px 44px;border-radius:8px">%s</a></td></tr>
                </table>
                """.formatted(html(buttonUrl), html(buttonLabel));
        String secondaryParagraph = secondary == null ? "" : paragraph(secondary);
        String noShowParagraph = noShowPolicy == null ? "" : "<p style=\"font-family:Arial,sans-serif;font-size:15px;line-height:1.55;margin:0 0 18px\"><strong>No-show policy:</strong> "
                + html(noShowPolicy) + "</p>";
        return """
                <!doctype html>
                <html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>%s</title></head>
                <body style="margin:0;padding:0;background:#f6f3ef;color:#231f1c">
                  <div style="display:none;max-height:0;overflow:hidden;opacity:0">%s</div>
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background:#f6f3ef"><tr><td align="center" style="padding:28px 12px">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="max-width:640px;background:#ffffff;border:1px solid #eee7df;border-radius:12px;box-shadow:0 3px 18px rgba(55,36,25,.07)">
                      <tr><td style="padding:46px 40px 38px">
                        <div style="font-family:Georgia,'Times New Roman',serif;font-size:36px;letter-spacing:5px;text-align:center;color:#4a2115">AH BRAIDING</div>
                        <div style="height:1px;background:#c78b2d;margin:28px 0 38px"></div>
                        <h1 style="font-family:Georgia,'Times New Roman',serif;font-size:42px;line-height:1.08;text-align:center;color:#4a2115;margin:0 0 38px">%s</h1>
                        <p style="font-family:Arial,sans-serif;font-size:17px;line-height:1.6;margin:0 0 22px">Hi %s,</p>
                        %s%s
                        %s
                        <div style="background:#fbf7f1;border:1px solid #ead9c7;border-radius:10px;padding:20px 24px;margin:24px 0;font-family:Arial,sans-serif;font-size:16px;line-height:1.55">%s</div>
                        <p style="font-family:Arial,sans-serif;font-size:16px;line-height:1.6;margin:0 0 18px">%s</p>
                        %s
                        %s
                        <p style="font-family:Arial,sans-serif;font-size:16px;line-height:1.6;text-align:center;margin:0 0 28px">Questions? Reply to this email or call/text %s.</p>
                        <div style="height:1px;background:#c78b2d;margin:0 0 24px"></div>
                        %s
                      </td></tr>
                    </table>
                  </td></tr></table>
                </body></html>
                """.formatted(
                html(heading), html(heading), html(heading), html(firstName(appointment)),
                paragraph(intro), secondaryParagraph,
                htmlDetails(appointment, depositLabel), html(notice), html(closing), noShowParagraph, button,
                html(phone), htmlFooter());
    }

    private String paragraph(String text) {
        return "<p style=\"font-family:Arial,sans-serif;font-size:17px;line-height:1.6;margin:0 0 22px\">"
                + html(text) + "</p>";
    }

    private String htmlDetails(Appointment appointment, String depositLabel) {
        StringBuilder rows = new StringBuilder();
        detailRow(rows, "Date and time", dateTime(appointment) + " CT");
        detailRow(rows, "Service", serviceName(appointment));
        if (hasSizeSelection(appointment)) detailRow(rows, "Size", appointment.getSelectedSize());
        detailRow(rows, "Length", appointment.getSelectedLength());
        detailRow(rows, "Foundation", friendlyFoundation(appointment.getSelectedFoundation()));
        detailRow(rows, "Texture", appointment.getSelectedTexture());
        if (appointment.getAddOns() != null && !appointment.getAddOns().isEmpty()) {
            detailRow(rows, "Add-ons", appointment.getAddOns().stream()
                    .map(AppointmentAddOn::getAddOnName).collect(Collectors.joining(", ")));
        }
        detailRow(rows, depositLabel, money("Deposit charged".equals(depositLabel)
                ? capturedAmount(appointment) : authorizedAmount(appointment)));
        return "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" "
                + "style=\"background:#fbf7f1;border:1px solid #ead9c7;border-radius:10px;margin:24px 0;border-collapse:separate\">"
                + rows + "</table>";
    }

    private void detailRow(StringBuilder rows, String label, String value) {
        if (value == null || value.isBlank()) return;
        rows.append("<tr><td style=\"padding:15px 20px;border-bottom:1px solid #eadfd3;font-family:Arial,sans-serif;font-size:15px;font-weight:700;color:#4a2115;vertical-align:top;width:34%\">")
                .append(html(label)).append(":</td><td style=\"padding:15px 20px;border-bottom:1px solid #eadfd3;font-family:Arial,sans-serif;font-size:15px;line-height:1.45;vertical-align:top\">")
                .append(html(value)).append("</td></tr>");
    }

    private String htmlFooter() {
        StringBuilder footer = new StringBuilder("<div style=\"font-family:Arial,sans-serif;font-size:14px;line-height:1.55;text-align:center;color:#40352f\">")
                .append("<strong style=\"font-family:Georgia,'Times New Roman',serif;font-size:20px;color:#4a2115\">").append(html(salonName)).append("</strong><br>")
                .append(html(addressLine1)).append("<br>").append(html(addressLine2)).append("<br>")
                .append(html(hoursWeekday)).append("<br>").append(html(hoursSunday));
        if (!website.isBlank()) footer.append("<br><a href=\"").append(html(website)).append("\" style=\"color:#632b14\">Website</a>");
        return footer.append("</div>").toString();
    }

    private long authorizedAmount(Appointment appointment) {
        return appointment.getAmountAuthorized() == null ? depositAmount(appointment) : appointment.getAmountAuthorized();
    }

    private String html(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    public Notification denied(Appointment appointment) {
        String body = brandedEmail(
                "Update on your appointment request",
                "Unfortunately, we’re unable to accommodate this appointment request.",
                "Reason: " + reason(appointment),
                appointment,
                cancellationDepositLabel(appointment),
                authorizationReleaseText(appointment).trim(),
                "You may submit a new request for another available time.",
                null,
                null,
                null);
        return new Notification("Update on your " + salonName + " appointment request", body,
                "Hi " + firstName(appointment) + ", we’re unable to accommodate your appointment request for "
                        + dateTime(appointment) + " CT. Reason: " + reason(appointment) + " "
                        + shortPaymentOutcome(appointment) + " Call/text " + phone + " to reschedule.");
    }

    public Notification cancelled(Appointment appointment) {
        String body = brandedEmail(
                "Your appointment has been cancelled",
                "Your appointment has been cancelled and the appointment time has been released.",
                "Cancellation reason: " + reason(appointment),
                appointment,
                cancellationDepositLabel(appointment),
                cancellationPaymentNotice(appointment),
                "You may submit a new request whenever you are ready to book another available time.",
                null,
                null,
                null);
        return new Notification("Your " + salonName + " appointment has been cancelled", body,
                "Hi " + firstName(appointment) + ", your appointment for " + dateTime(appointment)
                        + " CT was cancelled. Reason: " + reason(appointment) + " "
                        + shortPaymentOutcome(appointment) + " Questions? " + phone);
    }

    public Notification expired(Appointment appointment) {
        String body = brandedEmail(
                "Your appointment request expired",
                "The payment step was not completed before the reservation expired, so the appointment time has been released.",
                null,
                appointment,
                cancellationDepositLabel(appointment),
                cancellationPaymentNotice(appointment),
                "No appointment is confirmed. You may submit a new request for any available time.",
                null,
                null,
                null);
        return new Notification("Your appointment request expired", body,
                "Hi " + firstName(appointment) + ", your appointment request for " + dateTime(appointment)
                        + " CT expired and the time was released. " + shortPaymentOutcome(appointment));
    }

    public Notification noShowPaid(Appointment appointment, long servicePriceCents, long totalFeeCents,
                                   long depositCreditCents, long additionalChargeCents) {
        return noShowPaid(appointment, servicePriceCents, totalFeeCents, depositCreditCents, additionalChargeCents, false);
    }

    public Notification noShowPaid(Appointment appointment, long servicePriceCents, long totalFeeCents,
                                   long depositCreditCents, long additionalChargeCents, boolean adjusted) {
        String breakdown = "Scheduled service price: " + money(servicePriceCents)
                + (adjusted ? ". Adjusted no-show fee: " : ". Total 60% no-show fee: ") + money(totalFeeCents)
                + ". Deposit applied: " + money(depositCreditCents)
                + ". Saved-card charge: " + money(additionalChargeCents) + ".";
        String body = brandedEmail(
                "No-show fee receipt",
                "Your appointment was marked as a no-show and the no-show fee has been paid.",
                breakdown,
                appointment,
                "Deposit credit",
                money(additionalChargeCents) + " was charged to your saved card.",
                "The no-show balance is resolved and the booking restriction has been removed.",
                null,
                null,
                null);
        return new Notification("Your " + salonName + " no-show fee receipt", body, "");
    }

    public Notification noShowFailed(Appointment appointment, long servicePriceCents, long totalFeeCents,
                                     long depositCreditCents, long additionalChargeCents) {
        return noShowFailed(appointment, servicePriceCents, totalFeeCents, depositCreditCents, additionalChargeCents, false);
    }

    public Notification noShowFailed(Appointment appointment, long servicePriceCents, long totalFeeCents,
                                     long depositCreditCents, long additionalChargeCents, boolean adjusted) {
        String breakdown = "Scheduled service price: " + money(servicePriceCents)
                + (adjusted ? ". Adjusted no-show fee: " : ". Total 60% no-show fee: ") + money(totalFeeCents)
                + ". Deposit applied: " + money(depositCreditCents)
                + ". Remaining balance: " + money(additionalChargeCents) + ".";
        String body = brandedEmail(
                "No-show balance requires attention",
                "Your appointment was marked as a no-show, but the remaining balance could not be charged to the saved card.",
                breakdown,
                appointment,
                "Deposit credit",
                "The remaining balance is still unpaid.",
                "You cannot submit another appointment request until this balance is resolved. Call or text " + phone + " for assistance.",
                null,
                null,
                null);
        return new Notification("Action required: your " + salonName + " no-show balance", body, "");
    }

    public Notification noShowWaived(Appointment appointment) {
        String body = brandedEmail(
                "No-show fee waived",
                "Your appointment was marked as a no-show, but the additional no-show fee was waived by the salon.",
                null,
                appointment,
                "Fee waived",
                "No additional no-show payment is due.",
                "There is no outstanding no-show balance on your account.",
                null,
                null,
                null);
        return new Notification("Your " + salonName + " no-show fee was waived", body, "");
    }

    private String greeting(Appointment appointment) {
        return "Hi " + firstName(appointment) + ",\n\n";
    }

    private String summary(Appointment appointment, String depositLabel) {
        StringBuilder value = new StringBuilder("APPOINTMENT DETAILS\n")
                .append("Date and time: ").append(dateTime(appointment)).append(" CT\n")
                .append("Service: ").append(serviceName(appointment)).append("\n");
        if (hasSizeSelection(appointment)) append(value, "Size", appointment.getSelectedSize());
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

    private boolean hasSizeSelection(Appointment appointment) {
        return appointment.getSelectedSize() != null
                && !appointment.getSelectedSize().isBlank()
                && !(appointment.getService() != null
                && "FIXED".equalsIgnoreCase(appointment.getService().getPricingMode())
                && (appointment.getService().getLengthOptions() == null
                || appointment.getService().getLengthOptions().isEmpty()));
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
        return value
                // UTF-8 en dash decoded as Windows-1252 (the production symptom).
                .replace("\u00e2\u20ac\u201c", "\u2013")
                // UTF-8 en dash decoded as ISO-8859-1 and retained as controls.
                .replace("\u00e2\u0080\u0093", "\u2013")
                .replace("\u2014", "\u2013");
    }

    private String authorizationReleaseText(Appointment appointment) {
        return switch (paymentStatus(appointment)) {
            case CAPTURED -> "The deposit was already captured and remains non-refundable under the policy accepted when booking.\n\n";
            case AUTHORIZED -> "The payment authorization is being released; your deposit was not charged. Your bank may take a few business days to remove a pending hold.\n\n";
            case CANCELLATION_FAILED -> "Your appointment request is cancelled, but we could not confirm release of the payment authorization. Your deposit has not been captured; our team will review the authorization status.\n\n";
            default -> "No deposit was charged for this request.\n\n";
        };
    }

    private String cancellationPaymentText(Appointment appointment) {
        return switch (paymentStatus(appointment)) {
            case CAPTURED -> "As stated in the policy accepted during booking, the captured deposit of "
                    + money(capturedAmount(appointment)) + " is non-refundable.\n\n";
            case AUTHORIZED -> "The payment authorization is being released; your deposit was not charged. Your bank may take a few business days to remove a pending hold.\n\n";
            case CANCELLATION_FAILED -> "Your appointment is cancelled, but we could not confirm release of the payment authorization. Your deposit has not been captured; our team will review the authorization status.\n\n";
            default -> "No deposit was charged for this appointment.\n\n";
        };
    }

    private String cancellationDepositLabel(Appointment appointment) {
        return paymentStatus(appointment) == Appointment.PaymentStatus.CAPTURED
                ? "Deposit charged" : "Deposit authorization";
    }

    private String cancellationPaymentNotice(Appointment appointment) {
        return cancellationPaymentText(appointment).trim();
    }

    private String shortPaymentOutcome(Appointment appointment) {
        return switch (paymentStatus(appointment)) {
            case CAPTURED -> "The captured deposit is non-refundable.";
            case AUTHORIZED -> "The authorization was released; your deposit was not charged.";
            case CANCELLATION_FAILED -> "The deposit was not captured, but authorization release requires review.";
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

    private String customerName(Appointment appointment) {
        String first = nullToEmpty(appointment.getCustomer().getFirstName()).trim();
        String last = nullToEmpty(appointment.getCustomer().getLastName()).trim();
        String full = (first + " " + last).trim();
        return full.isBlank() ? "Customer" : full;
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
