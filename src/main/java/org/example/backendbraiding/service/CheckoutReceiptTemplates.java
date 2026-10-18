package org.example.backendbraiding.service;

import org.example.backendbraiding.model.Appointment;
import org.example.backendbraiding.model.AppointmentCheckout;
import org.example.backendbraiding.model.CheckoutTransaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

@Component
public class CheckoutReceiptTemplates {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a");

    @Value("${salon.name:AH Braiding}")
    private String salonName;

    public Receipt checkout(Appointment appointment, AppointmentCheckout checkout, CheckoutTransaction payment) {
        String service = serviceName(appointment);
        String method = method(payment.getPaymentMethod());
        String details = rows(
                row("Service total", money(checkout.getServiceTotalCents())),
                row("Deposit credit", "−" + money(checkout.getDepositCreditCents())),
                checkout.getTipCents() > 0 ? row("Tip", money(checkout.getTipCents())) : "",
                row("Final payment", money(payment.getAmountCents())),
                row("Total paid", money(checkout.getServiceTotalCents() + checkout.getTipCents()))
        );
        String email = document("Payment receipt",
                "Thank you, " + escape(appointment.getCustomer().getFirstName()) + ".",
                "Your appointment is complete and your payment has been recorded.",
                "<p><b>Service:</b> " + escape(service) + "<br><b>Appointment:</b> "
                        + escape(appointment.getAppointmentDateTime().format(DATE)) + " CT<br><b>Payment method:</b> "
                        + escape(method) + "</p>" + details);
        String sms = salonName + " receipt: " + service + ". Total paid "
                + money(checkout.getServiceTotalCents() + checkout.getTipCents()) + " via " + method + ". Thank you.";
        return new Receipt("Your " + salonName + " payment receipt", email, sms);
    }

    public Receipt refund(Appointment appointment, AppointmentCheckout checkout, CheckoutTransaction refund) {
        String email = document("Refund receipt",
                "A refund was recorded for your appointment.",
                "Refunds paid outside Stripe are returned using the method agreed with the salon.",
                rows(row("Service", escape(serviceName(appointment))),
                        row("Refund amount", money(refund.getAmountCents())),
                        row("Reason", escape(refund.getReason()))));
        String sms = salonName + " refund receipt: " + money(refund.getAmountCents())
                + " for " + serviceName(appointment) + ".";
        return new Receipt("Your " + salonName + " refund receipt", email, sms);
    }

    private String document(String title, String heading, String intro, String content) {
        return "<!doctype html><html><body style=\"font-family:Arial,sans-serif;color:#351d12\">"
                + "<div style=\"max-width:600px;margin:auto;padding:24px\"><p style=\"color:#b9532f\">"
                + escape(salonName) + "</p><h1>" + escape(title) + "</h1><h2>" + heading + "</h2><p>"
                + intro + "</p>" + content + "</div></body></html>";
    }

    private String rows(String... rows) {
        return "<table style=\"width:100%;border-collapse:collapse\">" + String.join("", rows) + "</table>";
    }

    private String row(String label, String value) {
        return "<tr><td style=\"padding:8px 0;border-bottom:1px solid #eee\">" + escape(label)
                + "</td><td style=\"padding:8px 0;border-bottom:1px solid #eee;text-align:right\">" + value + "</td></tr>";
    }

    private String serviceName(Appointment appointment) {
        if (appointment.getSelectedService() != null && !appointment.getSelectedService().isBlank()) return appointment.getSelectedService();
        if (appointment.getService() != null && appointment.getService().getName() != null) return appointment.getService().getName();
        return "Braiding service";
    }

    private String method(CheckoutTransaction.PaymentMethod method) {
        return switch (method) {
            case CASH -> "Cash";
            case ZELLE -> "Zelle";
            case CASH_APP -> "Cash App";
            case OTHER -> "Other";
            case STRIPE_TERMINAL -> "Card";
        };
    }

    private String money(long cents) {
        return "$" + BigDecimal.valueOf(cents, 2).setScale(2).toPlainString();
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    public record Receipt(String subject, String emailBody, String smsBody) {}
}
