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

        List<Customer> customers = customerRepository.findAll();
        List<Long> customerIds = customers.stream().map(Customer::getId).toList();
        List<Appointment> appointments = customerIds.isEmpty() ? List.of() : appointmentRepository.findByCustomerIdIn(customerIds);
        Map<Long, List<Appointment>> byCustomer = appointments.stream()
                .collect(Collectors.groupingBy(item -> item.getCustomer().getId()));

        List<CustomerSummaryDTO> summaries = customers.stream()
                .filter(customer -> matches(customer, needle))
                .map(customer -> mapToSummaryDTO(customer, byCustomer.getOrDefault(customer.getId(), List.of())))
                .filter(summary -> matchesSegment(summary, byCustomer.getOrDefault(summary.id(), List.of()), safeSegment))
                .sorted(summaryComparator(sort))
                .toList();

        int from = Math.min(safePage * safeSize, summaries.size());
        int to = Math.min(from + safeSize, summaries.size());
        return new PageImpl<>(summaries.subList(from, to), PageRequest.of(safePage, safeSize), summaries.size());
    }

    public CustomerDetailDTO getCustomerDetails(Long id, int appointmentPage, int appointmentSize, String appointmentStatus) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        int safePage = Math.max(0, appointmentPage);
        int safeSize = Math.max(1, Math.min(appointmentSize, 50));
        String safeStatus = normalizeOption(appointmentStatus, "ALL");
        List<Appointment> allAppointments = appointmentRepository.findByCustomerId(id);
        return mapToDetailDTO(customer, allAppointments, safePage, safeSize, safeStatus);
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

    private CustomerDetailDTO mapToDetailDTO(Customer customer, List<Appointment> appointments,
                                              int page, int size, String status) {
        LocalDateTime now = LocalDateTime.now();
        List<Appointment> visits = appointments.stream().filter(CustomerAnalytics::isVisit).toList();
        LocalDateTime firstVisit = visits.stream().map(Appointment::getAppointmentDateTime).min(Comparator.naturalOrder()).orElse(null);
        LocalDateTime lastVisit = visits.stream().filter(item -> !item.getAppointmentDateTime().isAfter(now))
                .map(Appointment::getAppointmentDateTime).max(Comparator.naturalOrder()).orElse(null);
        LocalDateTime nextAppointment = appointments.stream().filter(item -> CustomerAnalytics.isUpcoming(item, now))
                .map(Appointment::getAppointmentDateTime).min(Comparator.naturalOrder()).orElse(null);
        int completedVisits = (int) appointments.stream().filter(item -> item.getStatus() == Appointment.AppointmentStatus.COMPLETED).count();
        int upcoming = (int) appointments.stream().filter(item -> CustomerAnalytics.isUpcoming(item, now)).count();
        BigDecimal totalSpent = CustomerAnalytics.capturedTotal(appointments);
        long capturedCount = appointments.stream().filter(item -> item.getPaymentStatus() == Appointment.PaymentStatus.CAPTURED).count();
        BigDecimal averagePaid = capturedCount == 0 ? BigDecimal.ZERO
                : totalSpent.divide(BigDecimal.valueOf(capturedCount), 2, RoundingMode.HALF_UP);

        List<Appointment> filtered = appointments.stream()
                .filter(item -> "ALL".equals(status) || item.getStatus().name().equals(status))
                .sorted(Comparator.comparing(Appointment::getAppointmentDateTime).reversed())
                .toList();
        int from = Math.min(page * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        List<CustomerDetailDTO.AppointmentSummaryDTO> history = filtered.subList(from, to).stream()
                .map(this::appointmentSummary).toList();
        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / size);

        return new CustomerDetailDTO(customer.getId(), customer.getFirstName(), customer.getLastName(),
                customer.getEmail(), customer.getPhoneNumber(), firstVisit, lastVisit, nextAppointment,
                appointments.size(), completedVisits, upcoming, totalSpent, averagePaid, history,
                page, totalPages, filtered.size(), null);
    }

    private CustomerDetailDTO.AppointmentSummaryDTO appointmentSummary(Appointment appointment) {
        return new CustomerDetailDTO.AppointmentSummaryDTO(appointment.getId(), CustomerAnalytics.serviceName(appointment),
                appointment.getAppointmentDateTime(), appointment.getAppointmentEndDateTime(),
                appointment.getDurationMinutes(), appointment.getStatus().name(),
                appointment.getPaymentStatus() == null ? null : appointment.getPaymentStatus().name(),
                CustomerAnalytics.capturedAmount(appointment));
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
