package org.example.backendbraiding.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "blocked_time_slots")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlockedTimeSlot {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private LocalDateTime startDateTime;
    
    @Column(nullable = false)
    private LocalDateTime endDateTime;
    
    @Column(nullable = false)
    private String reason;
    
    @Column(nullable = false)
    private Boolean isRecurring = false;
    
    @Column
    private String recurrencePattern; // e.g., "WEEKLY", "DAILY", "MONTHLY"
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Admin createdBy;
    
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
