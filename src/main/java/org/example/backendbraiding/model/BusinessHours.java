package org.example.backendbraiding.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Entity
@Table(name = "business_hours")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BusinessHours {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private DayOfWeek dayOfWeek;
    
    @Column(nullable = false)
    private LocalTime openTime;
    
    @Column(nullable = false)
    private LocalTime closeTime;
    
    @Column(nullable = false)
    private Boolean isOpen = true;
    
    @Column
    private String notes;
}
