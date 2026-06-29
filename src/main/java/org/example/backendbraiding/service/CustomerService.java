package org.example.backendbraiding.service;

import org.example.backendbraiding.dto.CustomerDetailDTO;
import org.example.backendbraiding.dto.CustomerSummaryDTO;
import org.example.backendbraiding.model.Appointment;
import org.example.backendbraiding.model.Customer;
import org.example.backendbraiding.repository.AppointmentRepository;
import org.example.backendbraiding.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CustomerService {
    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;

    public CustomerService(AppointmentRepository appointmentRepository, CustomerRepository customerRepository) {
        this.appointmentRepository = appointmentRepository;
        this.customerRepository = customerRepository;
    }

    public List<CustomerSummaryDTO> getAllCustomers() {
        List<Appointment> allAppointments = appointmentRepository.findAll();
        
        // Aggregate customer data from appointments
        Map<Long, List<Appointment>> appointmentsByCustomer = allAppointments.stream()
                .collect(Collectors.groupingBy(a -> a.getCustomer().getId()));
        
        return appointmentsByCustomer.entrySet().stream()
                .map(entry -> {
                    Customer customer = entry.getValue().get(0).getCustomer();
                    List<Appointment> customerAppointments = entry.getValue();
                    
                    LocalDateTime lastAppointment = customerAppointments.stream()
                            .map(Appointment::getAppointmentDateTime)
                            .max(Comparator.naturalOrder())
                            .orElse(null);
                    
                    BigDecimal totalSpent = customerAppointments.stream()
                            .map(app -> {
                                try {
                                    String priceStr = app.getService() != null ? app.getService().getPrice() : "0";
                                    return new BigDecimal(priceStr);
                                } catch (Exception e) {
                                    return BigDecimal.ZERO;
                                }
                            })
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    
                    return new CustomerSummaryDTO(
                            customer.getId(),
                            customer.getFirstName(),
                            customer.getLastName(),
                            customer.getEmail(),
                            customer.getPhoneNumber(),
                            lastAppointment,
                            customerAppointments.size(),
                            totalSpent
                    );
                })
                .sorted(Comparator.comparing(CustomerSummaryDTO::getLastAppointmentDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    public CustomerDetailDTO getCustomerDetails(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found"));
        
        List<Appointment> appointments = appointmentRepository.findByCustomerId(customerId);
        
        LocalDateTime firstAppointment = appointments.stream()
                .map(Appointment::getAppointmentDateTime)
                .min(Comparator.naturalOrder())
                .orElse(null);
        
        LocalDateTime lastAppointment = appointments.stream()
                .map(Appointment::getAppointmentDateTime)
                .max(Comparator.naturalOrder())
                .orElse(null);
        
        BigDecimal totalSpent = appointments.stream()
                .map(app -> {
                    try {
                        String priceStr = app.getService() != null ? app.getService().getPrice() : "0";
                        return new BigDecimal(priceStr);
                    } catch (Exception e) {
                        return BigDecimal.ZERO;
                    }
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal averageValue = appointments.isEmpty() ? BigDecimal.ZERO :
                totalSpent.divide(BigDecimal.valueOf(appointments.size()), 2, java.math.RoundingMode.HALF_UP);
        
        List<CustomerDetailDTO.AppointmentSummaryDTO> appointmentSummaries = appointments.stream()
                .sorted(Comparator.comparing(Appointment::getAppointmentDateTime).reversed())
                .map(app -> {
                    try {
                        String priceStr = app.getService() != null ? app.getService().getPrice() : "0";
                        BigDecimal price = new BigDecimal(priceStr);
                        return new CustomerDetailDTO.AppointmentSummaryDTO(
                                app.getId(),
                                app.getService() != null ? app.getService().getName() : "Unknown",
                                app.getAppointmentDateTime(),
                                app.getStatus().name(),
                                price
                        );
                    } catch (Exception e) {
                        return new CustomerDetailDTO.AppointmentSummaryDTO(
                                app.getId(),
                                "Unknown",
                                app.getAppointmentDateTime(),
                                app.getStatus().name(),
                                BigDecimal.ZERO
                        );
                    }
                })
                .collect(Collectors.toList());
        
        return new CustomerDetailDTO(
                customer.getId(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                customer.getPhoneNumber(),
                firstAppointment,
                lastAppointment,
                appointments.size(),
                totalSpent,
                averageValue,
                appointmentSummaries,
                null // Notes - can be added later
        );
    }
}
