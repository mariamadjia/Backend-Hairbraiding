package org.example.backendbraiding.service;

public final class BookingPolicySnapshot {
    private BookingPolicySnapshot() {}

    public static String fullText(long depositCents) {
        String deposit = String.format("%.2f", depositCents / 100.0);
        StringBuilder text = new StringBuilder();
        text.append("ACCEPTANCE STATEMENT\nI have read and agree to AH Braiding Salon's Booking Policies.\n\n");
        section(text, "Appointments, Deposits & Approval",
                "All braiding services are available by appointment.",
                depositCents == 0
                        ? "No deposit or credit card is required for this appointment request."
                        : "A card authorization of $" + deposit + " is required to submit your appointment request. Your card is charged when the appointment is approved, automatically or after salon review according to the booking policy.",
                depositCents == 0
                        ? "Your request is still subject to salon approval unless automatic confirmation is enabled."
                        : "Once approved, the $" + deposit + " deposit is non-refundable and will be applied toward your total service balance. If your request is denied, the authorization hold is released.",
                depositCents == 0
                        ? "Please note that all payments made directly to the salon are final and non-refundable."
                        : "Please note that all payments are final and non-refundable. No refunds will be issued under any circumstances.");
        section(text, "Hair & Appointment Preparation",
                "Please arrive with clean, fully detangled hair unless a wash and blow-dry service has been scheduled.",
                "Additional detangling or preparation fees may apply.",
                "You are responsible for providing the correct type, color, and quantity of extension hair unless hair is included with your selected service.",
                "Additional hair may result in an additional charge.",
                "Please inform your stylist of any scalp sensitivity, recent chemical services, hair loss, or other concerns before your appointment.",
                "The stylist may decline or modify a service if your hair or scalp appears unsuitable for braiding.");
        section(text, "Late Arrivals",
                "Please arrive on time. A 30-minute grace period is provided.",
                "Arrivals beyond the grace period may result in a shortened service, an additional fee, or rescheduling.",
                "Your deposit may be forfeited if the appointment cannot be completed.");
        if (depositCents == 0) {
            section(text, "Cancellations, Rescheduling & No-Shows",
                    "At least 72 hours' notice is required to cancel or reschedule.",
                    "An appointment is considered a no-show when a client misses their scheduled appointment without providing notice.",
                    "No card-based no-show fee will be charged for a $0-deposit booking because no payment method is collected.",
                    "Repeated no-shows may result in the loss of future booking privileges.");
        } else {
            section(text, "Cancellations, Rescheduling & No-Shows",
                    "At least 72 hours' notice is required to cancel or reschedule.",
                    "Late cancellations and no-shows may result in loss of the deposit and/or a requirement to pay a new deposit before booking another appointment.",
                    "An appointment is considered a no-show when a client misses their scheduled appointment without providing notice.",
                    "Missing your appointment without notice will result in a non-refundable fee equal to 60% of the scheduled service price. Your deposit is applied toward this fee.",
                    "By accepting these policies, you authorize AH Braiding Salon to save the card used for your deposit and charge the remaining no-show balance to that card. The charge is normally processed within 24 hours after the missed appointment and no later than seven calendar days afterward.",
                    "Repeated no-shows may result in the loss of future booking privileges.");
        }
        section(text, "Photos & Promotional Use",
                "AH Braiding Salon may photograph or record completed hairstyles for use on our website, social media, portfolio, and other promotional materials.",
                "If you prefer not to be photographed or recorded, please notify your stylist before your service begins. We will respect your request.");
        return text.toString().trim();
    }

    private static void section(StringBuilder text, String heading, String... points) {
        text.append(heading).append('\n');
        for (String point : points) text.append("- ").append(point).append('\n');
        text.append('\n');
    }
}
