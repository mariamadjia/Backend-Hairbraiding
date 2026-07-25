package org.example.backendbraiding.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "pricing_history")
@Data
public class PricingHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_item_id")
    private Long serviceItemId;

    @Column(name = "service_name", nullable = false, length = 120)
    private String serviceName;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(nullable = false, length = 1000)
    private String summary;

    @Column(name = "changed_by")
    private String changedBy;

    @Column(nullable = false, length = 40)
    private String source = "SYSTEM";

    @Column(name = "batch_id", length = 64)
    private String batchId;

    @Column(name = "before_value", columnDefinition = "TEXT")
    private String beforeValue;

    @Column(name = "after_value", columnDefinition = "TEXT")
    private String afterValue;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
