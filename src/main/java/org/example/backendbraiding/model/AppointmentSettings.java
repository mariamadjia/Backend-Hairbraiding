package org.example.backendbraiding.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "appointment_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentSettings {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Integer slotDurationMinutes = 60;
    
    @Column(nullable = false)
    private Integer maxAppointmentsPerSlot = 1;
    
    @Column(nullable = false)
    private Integer advanceBookingDays = 60;
    
    @Column(nullable = false)
    private Integer bufferTimeBetweenAppointments = 0;
    
    @Column(nullable = false)
    private Boolean requireApproval = true;
    
    @Column(nullable = false)
    private Boolean allowSameDayBooking = true;
    
    @Column
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private Admin updatedBy;
}
