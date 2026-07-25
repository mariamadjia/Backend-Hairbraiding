package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.PricingHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PricingHistoryRepository extends JpaRepository<PricingHistory, Long> {
    List<PricingHistory> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
