package org.example.backendbraiding.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "appointment_events", indexes = @Index(name = "idx_appointment_events_appointment", columnList = "appointment_id, created_at"))
@Data
@NoArgsConstructor
public class AppointmentEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "actor_admin_id")
    private Admin actor;
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;
    @Column(name = "appointment_status", nullable = false, length = 30)
    private String appointmentStatus;
    @Column(name = "payment_status", length = 30)
    private String paymentStatus;
    @Column(length = 1000)
    private String reason;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
