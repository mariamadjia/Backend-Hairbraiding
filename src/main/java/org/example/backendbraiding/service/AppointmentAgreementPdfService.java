package org.example.backendbraiding.service;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.example.backendbraiding.exception.ResourceNotFoundException;
import org.example.backendbraiding.model.Appointment;
import org.example.backendbraiding.repository.AppointmentRepository;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AppointmentAgreementPdfService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a");
    private final AppointmentRepository appointmentRepository;

    public byte[] create(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

        String policy = appointment.getBookingPolicySnapshot();
        if (policy == null || policy.isBlank()) {
            if ("non-refundable-v1".equals(appointment.getDepositPolicyVersion())) {
                policy = BookingPolicySnapshot.fullText(appointment.getDepositAmount() == null
                        ? 0 : appointment.getDepositAmount());
            } else {
                policy = "Historical agreement record\n\n"
                        + "The exact policy wording was not stored for this appointment. "
                        + "The booking record shows policy version " + value(appointment.getDepositPolicyVersion())
                        + " accepted on " + date(appointment.getDepositPolicyAcceptedAt()) + ".";
            }
        }

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfLayout pdf = new PdfLayout(document);
            pdf.startPage();
            pdf.documentTitle("Customer booking agreement", "Appointment #" + appointment.getId());

            pdf.infoCard("CUSTOMER", List.of(
                    new Field("Name", customerName(appointment)),
                    new Field("Email", value(appointment.getCustomer().getEmail())),
                    new Field("Phone", value(appointment.getCustomer().getPhoneNumber()))));

            pdf.infoCard("APPOINTMENT", List.of(
                    new Field("Service", value(appointment.getSelectedService())),
                    new Field("Date & time", date(appointment.getAppointmentDateTime())),
                    new Field("Service price", money(appointment.getPrice())),
                    new Field("Deposit paid", cents(appointment.getDepositAmount()))));

            boolean accepted = appointment.getDepositPolicyAcceptedAt() != null;
            pdf.acceptanceCard(accepted, List.of(
                    new Field("Policy status", accepted ? "Accepted by customer" : "Acceptance not recorded"),
                    new Field("Accepted on", date(appointment.getDepositPolicyAcceptedAt())),
                    new Field("Policy version", value(appointment.getDepositPolicyVersion())),
                    new Field("Booking source", appointment.getBookingSource() == null
                            ? "CUSTOMER" : appointment.getBookingSource().name()),
                    new Field("No-show authorization", consent(appointment.getOffSessionConsentAt(),
                            appointment.getOffSessionConsentPolicyVersion()))));

            pdf.policy(policy);
            pdf.finish();
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Agreement PDF could not be generated", exception);
        }
    }

    private String customerName(Appointment appointment) {
        return (appointment.getCustomer().getFirstName() + " " + appointment.getCustomer().getLastName()).trim();
    }

    private String consent(LocalDateTime acceptedAt, String version) {
        if (acceptedAt == null) return "Not recorded";
        return "Accepted " + date(acceptedAt) + " (" + value(version) + ")";
    }

    private String money(String amount) {
        if (amount == null || amount.isBlank()) return "Not recorded";
        String trimmed = amount.trim();
        return trimmed.startsWith("$") ? trimmed : "$" + trimmed;
    }

    private String cents(Long amount) {
        if (amount == null) return "$0.00";
        return String.format(Locale.US, "$%.2f", amount / 100.0);
    }

    private String value(Object value) {
        return value == null || value.toString().isBlank() ? "Not recorded" : value.toString();
    }

    private String date(LocalDateTime value) {
        return value == null ? "Not recorded" : value.format(DATE);
    }

    private record Field(String label, String value) {}

    private static final class PdfLayout {
        private static final float PAGE_WIDTH = PDRectangle.LETTER.getWidth();
        private static final float PAGE_HEIGHT = PDRectangle.LETTER.getHeight();
        private static final float LEFT = 48;
        private static final float RIGHT = 48;
        private static final float CONTENT_WIDTH = PAGE_WIDTH - LEFT - RIGHT;
        private static final Color ESPRESSO = new Color(54, 39, 32);
        private static final Color TERRACOTTA = new Color(180, 92, 64);
        private static final Color CREAM = new Color(250, 247, 241);
        private static final Color SAND = new Color(239, 228, 214);
        private static final Color INK = new Color(43, 38, 34);
        private static final Color MUTED = new Color(107, 98, 91);
        private static final Color GREEN = new Color(52, 119, 88);
        private static final Color GREEN_PALE = new Color(234, 245, 238);
        private static final Color AMBER_PALE = new Color(255, 246, 224);

        private final PDDocument document;
        private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final PDFont serif = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);
        private PDPage page;
        private PDPageContentStream content;
        private float y;

        private PdfLayout(PDDocument document) {
            this.document = document;
        }

        private void startPage() throws IOException {
            closeContent();
            page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            fill(ESPRESSO, 0, PAGE_HEIGHT - 70, PAGE_WIDTH, 70);
            text("AH BRAIDING", LEFT, PAGE_HEIGHT - 40, 12, bold, Color.WHITE);
            text("CUSTOMER CARE  |  BOOKING RECORD", PAGE_WIDTH - RIGHT - 196,
                    PAGE_HEIGHT - 40, 7.5f, bold, new Color(225, 206, 194));
            y = PAGE_HEIGHT - 104;
        }

        private void documentTitle(String title, String reference) throws IOException {
            text(title, LEFT, y, 26, serif, ESPRESSO);
            text(reference, LEFT, y - 21, 9.5f, bold, TERRACOTTA);
            y -= 51;
        }

        private void infoCard(String label, List<Field> fields) throws IOException {
            float height = 41 + fields.size() * 26;
            ensure(height + 14);
            fill(CREAM, LEFT, y - height, CONTENT_WIDTH, height);
            fill(TERRACOTTA, LEFT, y - height, 5, height);
            text(label, LEFT + 19, y - 23, 8, bold, TERRACOTTA);
            float rowY = y - 47;
            for (Field field : fields) {
                text(field.label(), LEFT + 19, rowY, 8.5f, bold, MUTED);
                textFit(field.value(), LEFT + 132, rowY, CONTENT_WIDTH - 153, 10.5f, regular, INK);
                rowY -= 26;
            }
            y -= height + 14;
        }

        private void acceptanceCard(boolean accepted, List<Field> fields) throws IOException {
            float height = 56 + fields.size() * 25;
            ensure(height + 20);
            Color accent = accepted ? GREEN : TERRACOTTA;
            Color background = accepted ? GREEN_PALE : AMBER_PALE;
            fill(background, LEFT, y - height, CONTENT_WIDTH, height);
            fill(accent, LEFT, y - height, 5, height);
            fill(accent, LEFT + 19, y - 36, 20, 20);
            text(accepted ? "✓" : "!", LEFT + 25, y - 31, 12, bold, Color.WHITE);
            text(accepted ? "Customer acceptance recorded" : "Acceptance record incomplete",
                    LEFT + 49, y - 30, 13, bold, INK);
            float rowY = y - 58;
            for (Field field : fields) {
                text(field.label(), LEFT + 19, rowY, 8.5f, bold, MUTED);
                textFit(field.value(), LEFT + 154, rowY, CONTENT_WIDTH - 175, 9.5f, regular, INK);
                rowY -= 25;
            }
            y -= height + 24;
        }

        private void policy(String policy) throws IOException {
            startPage();
            text("Terms agreed to", LEFT, y, 20, serif, ESPRESSO);
            text("This is the full policy presented during booking.", LEFT, y - 19, 8.5f, regular, MUTED);
            y -= 43;

            for (String raw : policy.split("\\R", -1)) {
                String line = sanitize(raw.trim());
                if (line.isBlank()) {
                    y -= 3;
                    continue;
                }
                if (isHeading(line)) {
                    ensure(38);
                    y -= 4;
                    text(line, LEFT, y, 10.5f, bold, ESPRESSO);
                    line(TERRACOTTA, LEFT, y - 8, LEFT + 34, y - 8, 2);
                    y -= 22;
                } else {
                    boolean bullet = line.matches("^[-*•].+");
                    String paragraph = bullet ? line.substring(1).trim() : line;
                    List<String> wrapped = wrap(paragraph, bullet ? CONTENT_WIDTH - 25 : CONTENT_WIDTH, 8.25f, regular);
                    ensure(wrapped.size() * 11 + 4);
                    if (bullet) fill(TERRACOTTA, LEFT + 3, y - 5, 4, 4);
                    float textX = bullet ? LEFT + 18 : LEFT;
                    for (String wrappedLine : wrapped) {
                        text(wrappedLine, textX, y, 8.25f, regular, INK);
                        y -= 11;
                    }
                    y -= 2;
                }
            }
        }

        private boolean isHeading(String line) {
            if (line.length() > 80) return false;
            String lower = line.toLowerCase(Locale.ROOT);
            return line.matches("^\\d+[.)].*")
                    || lower.equals("acceptance statement")
                    || lower.startsWith("deposit")
                    || lower.startsWith("appointments")
                    || lower.startsWith("hair &")
                    || lower.startsWith("photos &")
                    || lower.startsWith("cancellation")
                    || lower.startsWith("reschedul")
                    || lower.startsWith("late")
                    || lower.startsWith("no-show")
                    || lower.equals("historical agreement record");
        }

        private void ensure(float needed) throws IOException {
            if (y - needed < 56) startPage();
        }

        private void finish() throws IOException {
            closeContent();
            int total = document.getNumberOfPages();
            for (int i = 0; i < total; i++) {
                PDPage footerPage = document.getPage(i);
                try (PDPageContentStream footer = new PDPageContentStream(document, footerPage,
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    footer.setStrokingColor(SAND);
                    footer.setLineWidth(0.7f);
                    footer.moveTo(LEFT, 39);
                    footer.lineTo(PAGE_WIDTH - RIGHT, 39);
                    footer.stroke();
                    footer.beginText();
                    footer.setFont(regular, 7.5f);
                    footer.setNonStrokingColor(MUTED);
                    footer.newLineAtOffset(LEFT, 24);
                    footer.showText("AH Braiding  |  Customer booking agreement");
                    footer.endText();
                    String pageLabel = "Page " + (i + 1) + " of " + total;
                    float width = regular.getStringWidth(pageLabel) / 1000 * 7.5f;
                    footer.beginText();
                    footer.setFont(regular, 7.5f);
                    footer.setNonStrokingColor(MUTED);
                    footer.newLineAtOffset(PAGE_WIDTH - RIGHT - width, 24);
                    footer.showText(pageLabel);
                    footer.endText();
                }
            }
        }

        private List<String> wrap(String value, float maxWidth, float fontSize, PDFont font) throws IOException {
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String word : sanitize(value).split("\\s+")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (!current.isEmpty() && font.getStringWidth(candidate) / 1000 * fontSize > maxWidth) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                if (!current.isEmpty()) current.append(' ');
                current.append(word);
            }
            if (!current.isEmpty()) lines.add(current.toString());
            return lines.isEmpty() ? List.of("") : lines;
        }

        private void textFit(String value, float x, float textY, float maxWidth, float fontSize,
                             PDFont font, Color color) throws IOException {
            String safe = sanitize(value);
            if (font.getStringWidth(safe) / 1000 * fontSize <= maxWidth) {
                text(safe, x, textY, fontSize, font, color);
                return;
            }
            String suffix = "...";
            while (!safe.isEmpty() && font.getStringWidth(safe + suffix) / 1000 * fontSize > maxWidth) {
                safe = safe.substring(0, safe.length() - 1);
            }
            text(safe + suffix, x, textY, fontSize, font, color);
        }

        private void text(String value, float x, float textY, float fontSize, PDFont font, Color color)
                throws IOException {
            content.beginText();
            content.setFont(font, fontSize);
            content.setNonStrokingColor(color);
            content.newLineAtOffset(x, textY);
            content.showText(sanitize(value));
            content.endText();
        }

        private void fill(Color color, float x, float boxY, float width, float height) throws IOException {
            content.setNonStrokingColor(color);
            content.addRect(x, boxY, width, height);
            content.fill();
        }

        private void line(Color color, float x1, float y1, float x2, float y2, float width) throws IOException {
            content.setStrokingColor(color);
            content.setLineWidth(width);
            content.moveTo(x1, y1);
            content.lineTo(x2, y2);
            content.stroke();
        }

        private void closeContent() throws IOException {
            if (content != null) {
                content.close();
                content = null;
            }
        }

        private static String sanitize(String value) {
            if (value == null) return "Not recorded";
            return value.replace('\u2019', '\'')
                    .replace('\u2018', '\'')
                    .replace('\u201C', '"')
                    .replace('\u201D', '"')
                    .replace('\u2013', '-')
                    .replace('\u2014', '-')
                    .replace("•", "-")
                    .replace("✓", "OK");
        }
    }
}
