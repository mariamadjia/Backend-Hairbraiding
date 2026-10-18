package org.example.backendbraiding.service;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.example.backendbraiding.exception.ResourceNotFoundException;
import org.example.backendbraiding.model.Appointment;
import org.example.backendbraiding.repository.AppointmentRepository;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AppointmentAgreementPdfService {
    private final AppointmentRepository appointmentRepository;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a");

    public byte[] create(Long appointmentId) {
        Appointment a = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
        String policy = a.getBookingPolicySnapshot();
        if (policy == null || policy.isBlank()) {
            if ("non-refundable-v1".equals(a.getDepositPolicyVersion())) {
                policy = BookingPolicySnapshot.fullText(a.getDepositAmount() == null ? 0 : a.getDepositAmount());
            } else {
                policy = "Historical agreement record\nThe exact policy snapshot was not stored for this appointment. "
                        + "The database records policy version " + value(a.getDepositPolicyVersion())
                        + " as accepted at " + date(a.getDepositPolicyAcceptedAt()) + ".";
            }
        }
        List<String> lines = new ArrayList<>();
        lines.add("AH BRAIDING SALON"); lines.add("CUSTOMER BOOKING AGREEMENT"); lines.add("");
        lines.add("Appointment: #" + a.getId());
        lines.add("Customer: " + a.getCustomer().getFirstName() + " " + a.getCustomer().getLastName());
        lines.add("Email: " + value(a.getCustomer().getEmail()));
        lines.add("Phone: " + value(a.getCustomer().getPhoneNumber()));
        lines.add("Service: " + value(a.getSelectedService()));
        lines.add("Appointment time: " + date(a.getAppointmentDateTime()));
        lines.add("Service price: $" + value(a.getPrice()));
        lines.add("Deposit: $" + String.format("%.2f", (a.getDepositAmount() == null ? 0 : a.getDepositAmount()) / 100.0));
        lines.add(""); lines.add("ACCEPTANCE EVIDENCE");
        lines.add("Booking source: " + (a.getBookingSource() == null ? "CUSTOMER" : a.getBookingSource().name()));
        lines.add("Policy version: " + value(a.getDepositPolicyVersion()));
        lines.add("Policy accepted: " + date(a.getDepositPolicyAcceptedAt()));
        lines.add("No-show authorization version: " + value(a.getOffSessionConsentPolicyVersion()));
        lines.add("No-show authorization accepted: " + date(a.getOffSessionConsentAt()));
        lines.add(""); lines.add("AGREEMENT TEXT");
        for (String paragraph : policy.split("\\R")) lines.addAll(wrap(paragraph, 92));

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            int index = 0; int pageNumber = 1;
            while (index < lines.size()) {
                PDPage page = new PDPage(PDRectangle.LETTER); document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    float y = 742;
                    while (index < lines.size() && y > 60) {
                        String line = ascii(lines.get(index++));
                        boolean heading = line.equals("AH BRAIDING SALON") || line.equals("CUSTOMER BOOKING AGREEMENT") || line.equals("ACCEPTANCE EVIDENCE") || line.equals("AGREEMENT TEXT");
                        content.beginText(); content.setFont(heading ? bold : regular, heading ? 12 : 10);
                        content.newLineAtOffset(54, y); content.showText(line); content.endText();
                        y -= line.isBlank() ? 9 : 15;
                    }
                    content.beginText(); content.setFont(regular, 8); content.newLineAtOffset(500, 28);
                    content.showText("Page " + pageNumber++); content.endText();
                }
            }
            document.save(output); return output.toByteArray();
        } catch (IOException exception) { throw new IllegalStateException("Agreement PDF could not be generated", exception); }
    }

    private List<String> wrap(String text, int width) {
        if (text == null || text.isBlank()) return List.of("");
        List<String> result = new ArrayList<>(); StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (!line.isEmpty() && line.length() + word.length() + 1 > width) { result.add(line.toString()); line.setLength(0); }
            if (!line.isEmpty()) line.append(' '); line.append(word);
        }
        if (!line.isEmpty()) result.add(line.toString()); return result;
    }
    private String ascii(String value) { return value.replace('\u2019', '\'').replace('\u2018', '\'').replace('\u2013', '-').replace('\u2014', '-'); }
    private String value(Object value) { return value == null || value.toString().isBlank() ? "Not recorded" : value.toString(); }
    private String date(java.time.LocalDateTime value) { return value == null ? "Not recorded" : value.format(DATE); }
}
