package org.example.backendbraiding.service;

import lombok.extern.slf4j.Slf4j;
import org.example.backendbraiding.dto.CustomerDetailDTO;
import org.example.backendbraiding.dto.CustomerSummaryDTO;
import org.example.backendbraiding.model.Appointment;
import org.example.backendbraiding.model.Customer;
import org.example.backendbraiding.repository.AppointmentRepository;
import org.example.backendbraiding.repository.CustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CustomerService {
    private final CustomerRepository customerRepository;
    private final AppointmentRepository appointmentRepository;

    public CustomerService(CustomerRepository customerRepository, AppointmentRepository appointmentRepository) {
        this.customerRepository = customerRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @org.springframework.cache.annotation.Cacheable(value = "customers")
    public Page<CustomerSummaryDTO> getAllCustomers(Pageable pageable) {
        log.info("Fetching all customers with pagination");
        
        Page<Customer> customersPage = customerRepository.findAll(pageable);
        List<Customer> customers = customersPage.getContent();
        
        List<CustomerSummaryDTO> customerSummaries = customers.stream()
                .map(this::mapToSummaryDTO)
                .collect(Collectors.toList());
        
        return new PageImpl<>(customerSummaries, pageable, customersPage.getTotalElements());
    }

    @org.springframework.cache.annotation.Cacheable(value = "customers", key = "#id")
    public CustomerDetailDTO getCustomerDetails(Long id) {
        log.info("Fetching customer details for id: {}", id);
        
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + id));
        
        List<Appointment> appointments = appointmentRepository.findByCustomer_Id(id);
        
        return mapToDetailDTO(customer, appointments);
    }

    private CustomerSummaryDTO mapToSummaryDTO(Customer customer) {
        List<Appointment> appointments = appointmentRepository.findByCustomer_Id(customer.getId());
        
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
        
        return new CustomerSummaryDTO(
                customer.getId(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                customer.getPhoneNumber(),
                lastAppointment,
                appointments.size(),
                totalSpent
        );
    }

    private CustomerDetailDTO mapToDetailDTO(Customer customer, List<Appointment> appointments) {
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
        
        BigDecimal averageValue = appointments.isEmpty() ? BigDecimal.ZERO 
                : totalSpent.divide(BigDecimal.valueOf(appointments.size()), 2, java.math.RoundingMode.HALF_UP);
        
        List<CustomerDetailDTO.AppointmentSummaryDTO> appointmentSummaries = appointments.stream()
                .map(app -> new CustomerDetailDTO.AppointmentSummaryDTO(
                        app.getId(),
                        app.getService() != null ? app.getService().getName() : "Unknown",
                        app.getAppointmentDateTime(),
                        app.getStatus().name(),
                        app.getAmountPaid()
                ))
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
                null
        );
    }
}
