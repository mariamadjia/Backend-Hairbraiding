package org.example.backendbraiding.service;

import org.example.backendbraiding.dto.CustomerDetailDTO;
import org.example.backendbraiding.dto.CustomerSummaryDTO;
import org.example.backendbraiding.exception.ResourceNotFoundException;
import org.example.backendbraiding.model.Appointment;
import org.example.backendbraiding.model.Customer;
import org.example.backendbraiding.repository.AppointmentRepository;
import org.example.backendbraiding.repository.CustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CustomerService {
    private final CustomerRepository customerRepository;
    private final AppointmentRepository appointmentRepository;

    public CustomerService(CustomerRepository customerRepository, AppointmentRepository appointmentRepository) {
        this.customerRepository = customerRepository;
        this.appointmentRepository = appointmentRepository;
    }

    public Page<CustomerSummaryDTO> getAllCustomers(int page, int size, String query, String segment, String sort) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        String needle = normalizeSearch(query);
        String safeSegment = normalizeOption(segment, "ALL");

        String safeSort = normalizeOption(sort, "NAME_ASC");
        Page<CustomerRepository.CustomerSummaryView> result = customerRepository.findCustomerSummaries(
                needle, digits(needle), safeSegment, safeSort, PageRequest.of(safePage, safeSize));
        return result.map(item -> new CustomerSummaryDTO(item.getId(), item.getFirstName(), item.getLastName(),
                item.getEmail(), item.getPhoneNumber(), item.getLastAppointmentDate(), item.getNextAppointmentDate(),
                item.getTotalAppointments(), item.getCompletedVisits(),
                BigDecimal.valueOf(item.getCapturedCents() == null ? 0 : item.getCapturedCents(), 2)));
    }

    public CustomerDetailDTO getCustomerDetails(Long id, int appointmentPage, int appointmentSize, String appointmentStatus) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        int safePage = Math.max(0, appointmentPage);
        int safeSize = Math.max(1, Math.min(appointmentSize, 50));
        String safeStatus = normalizeOption(appointmentStatus, "ALL");
        org.springframework.data.domain.Pageable pageable = PageRequest.of(safePage, safeSize,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "appointmentDateTime"));
        Page<Appointment> history;
        if ("ALL".equals(safeStatus)) {
            history = appointmentRepository.findByCustomerId(id, pageable);
        } else {
            try {
                history = appointmentRepository.findByCustomerIdAndStatus(id,
                        Appointment.AppointmentStatus.valueOf(safeStatus), pageable);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unsupported appointment status filter");
            }
        }
        return mapToDetailDTO(customer, appointmentRepository.getCustomerStats(id), history);
    }

    private CustomerSummaryDTO mapToSummaryDTO(Customer customer, List<Appointment> appointments) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastVisit = appointments.stream()
                .filter(item -> CustomerAnalytics.isVisit(item) && !item.getAppointmentDateTime().isAfter(now))
                .map(Appointment::getAppointmentDateTime).max(Comparator.naturalOrder()).orElse(null);
        LocalDateTime nextAppointment = appointments.stream()
                .filter(item -> CustomerAnalytics.isUpcoming(item, now))
                .map(Appointment::getAppointmentDateTime).min(Comparator.naturalOrder()).orElse(null);
        int completedVisits = (int) appointments.stream().filter(item -> item.getStatus() == Appointment.AppointmentStatus.COMPLETED).count();
        return new CustomerSummaryDTO(customer.getId(), customer.getFirstName(), customer.getLastName(),
                customer.getEmail(), customer.getPhoneNumber(), lastVisit, nextAppointment,
                appointments.size(), completedVisits, CustomerAnalytics.capturedTotal(appointments));
    }

    private CustomerDetailDTO mapToDetailDTO(Customer customer, AppointmentRepository.CustomerStatsView stats,
                                              Page<Appointment> historyPage) {
        long capturedCount = stats.getCapturedCount() == null ? 0 : stats.getCapturedCount();
        BigDecimal totalSpent = BigDecimal.valueOf(stats.getCapturedCents() == null ? 0 : stats.getCapturedCents(), 2);
        BigDecimal averagePaid = capturedCount == 0 ? BigDecimal.ZERO
                : totalSpent.divide(BigDecimal.valueOf(capturedCount), 2, RoundingMode.HALF_UP);
        List<CustomerDetailDTO.AppointmentSummaryDTO> history = historyPage.getContent().stream()
                .map(this::appointmentSummary).toList();

        return new CustomerDetailDTO(customer.getId(), customer.getFirstName(), customer.getLastName(),
                customer.getEmail(), customer.getPhoneNumber(), stats.getFirstVisit(), stats.getLastVisit(), stats.getNextAppointment(),
                stats.getTotalAppointments(), stats.getCompletedVisits(), stats.getUpcomingAppointments(), totalSpent, averagePaid, history,
                historyPage.getNumber(), historyPage.getTotalPages(), historyPage.getTotalElements(), null);
    }

    private CustomerDetailDTO.AppointmentSummaryDTO appointmentSummary(Appointment appointment) {
        return new CustomerDetailDTO.AppointmentSummaryDTO(appointment.getId(), CustomerAnalytics.serviceName(appointment),
                appointment.getSelectedSize(), appointment.getSelectedLength(),
                appointment.getAppointmentDateTime(), appointment.getAppointmentEndDateTime(),
                appointment.getDurationMinutes(), appointment.getStatus().name(),
                appointment.getPaymentStatus() == null ? null : appointment.getPaymentStatus().name(),
                CustomerAnalytics.capturedAmount(appointment), paymentOutcome(appointment),
                appointment.getCancelledByCustomer(), appointment.getCustomerCancellationReason());
    }

    private String paymentOutcome(Appointment appointment) {
        if (appointment.getStatus() == Appointment.AppointmentStatus.CANCELLED) {
            if (appointment.getPaymentStatus() == Appointment.PaymentStatus.CAPTURED) return "DEPOSIT_RETAINED";
            if (appointment.getPaymentStatus() == Appointment.PaymentStatus.CANCELLATION_FAILED) return "RELEASE_FAILED";
            if (appointment.getPaymentStatus() == Appointment.PaymentStatus.CANCELLED) return "AUTHORIZATION_RELEASED";
        }
        if (appointment.getPaymentStatus() == Appointment.PaymentStatus.CAPTURED) return "DEPOSIT_CAPTURED";
        if (appointment.getPaymentStatus() == Appointment.PaymentStatus.AUTHORIZED) return "AUTHORIZED_NOT_CHARGED";
        return appointment.getPaymentStatus() == null ? "UNKNOWN" : appointment.getPaymentStatus().name();
    }

    private boolean matchesSegment(CustomerSummaryDTO summary, List<Appointment> appointments, String segment) {
        return switch (segment) {
            case "UPCOMING" -> summary.nextAppointmentDate() != null;
            case "COMPLETED" -> summary.completedVisits() > 0;
            case "CANCELLED" -> appointments.stream().anyMatch(item -> item.getStatus() == Appointment.AppointmentStatus.CANCELLED || item.getStatus() == Appointment.AppointmentStatus.DENIED);
            case "NO_UPCOMING" -> summary.nextAppointmentDate() == null;
            default -> true;
        };
    }

    private Comparator<CustomerSummaryDTO> summaryComparator(String sort) {
        Comparator<CustomerSummaryDTO> byName = Comparator.comparing(item -> (item.lastName() + " " + item.firstName()).toLowerCase(Locale.ROOT));
        return switch (normalizeOption(sort, "NAME_ASC")) {
            case "NAME_DESC" -> byName.reversed();
            case "LAST_VISIT" -> Comparator.comparing(CustomerSummaryDTO::lastAppointmentDate, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(byName);
            case "NEXT_APPOINTMENT" -> Comparator.comparing(CustomerSummaryDTO::nextAppointmentDate, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(byName);
            case "VALUE" -> Comparator.comparing(CustomerSummaryDTO::totalSpent).reversed().thenComparing(byName);
            case "APPOINTMENTS" -> Comparator.comparing(CustomerSummaryDTO::totalAppointments).reversed().thenComparing(byName);
            default -> byName;
        };
    }

    private boolean matches(Customer customer, String needle) {
        if (needle.isBlank()) return true;
        String text = (customer.getFirstName() + " " + customer.getLastName() + " " + customer.getEmail() + " " + customer.getPhoneNumber()).toLowerCase(Locale.ROOT);
        String phoneNeedle = digits(needle);
        return text.contains(needle) || (!phoneNeedle.isBlank() && digits(customer.getPhoneNumber()).contains(phoneNeedle));
    }

    private String normalizeSearch(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private String normalizeOption(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT); }
    private String digits(String value) { return value == null ? "" : value.replaceAll("\\D", ""); }
}
