package org.example.backendbraiding.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.backendbraiding.model.Appointment;
import org.example.backendbraiding.model.Customer;
import org.example.backendbraiding.repository.AppointmentRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentAgreementPdfServiceTests {
    @Test
    void createsAReadableBrandedAgreementWithTheFullPolicy() throws Exception {
        Appointment appointment = sampleAppointment();
        AppointmentRepository repository = (AppointmentRepository) Proxy.newProxyInstance(
                AppointmentRepository.class.getClassLoader(),
                new Class<?>[]{AppointmentRepository.class},
                (proxy, method, args) -> method.getName().equals("findById")
                        ? Optional.of(appointment)
                        : defaultValue(method.getReturnType()));

        byte[] bytes = new AppointmentAgreementPdfService(repository).create(37L);

        assertThat(bytes).startsWith("%PDF".getBytes());
        try (PDDocument document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(document.getNumberOfPages()).isEqualTo(2);
            assertThat(text).contains(
                    "AH BRAIDING",
                    "Customer booking agreement",
                    "Customer acceptance recorded",
                    "Appointments, Deposits & Approval",
                    "Photos & Promotional Use");
        }

        String previewPath = System.getProperty("agreement.preview.path");
        if (previewPath != null && !previewPath.isBlank()) Files.write(Path.of(previewPath), bytes);
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        return 0D;
    }

    private Appointment sampleAppointment() {
        Customer customer = new Customer();
        customer.setFirstName("Sariah");
        customer.setLastName("Yable");
        customer.setEmail("sariah@example.com");
        customer.setPhoneNumber("(757) 513-5135");

        Appointment appointment = new Appointment();
        appointment.setId(37L);
        appointment.setCustomer(customer);
        appointment.setSelectedService("Bohemian Box Braids");
        appointment.setAppointmentDateTime(LocalDateTime.of(2026, 10, 24, 9, 30));
        appointment.setPrice("180.00");
        appointment.setDepositAmount(5000L);
        appointment.setDepositPolicyVersion("non-refundable-v1");
        appointment.setDepositPolicyAcceptedAt(LocalDateTime.of(2026, 10, 17, 13, 42));
        appointment.setOffSessionConsentPolicyVersion("no-show-v1");
        appointment.setOffSessionConsentAt(LocalDateTime.of(2026, 10, 17, 13, 42));
        appointment.setBookingSource(Appointment.BookingSource.CUSTOMER);
        appointment.setBookingPolicySnapshot(BookingPolicySnapshot.fullText(5000));
        return appointment;
    }
}
