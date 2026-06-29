package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.BlockedTimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BlockedTimeSlotRepository extends JpaRepository<BlockedTimeSlot, Long> {
    
    @Query("SELECT b FROM BlockedTimeSlot b WHERE " +
           "(b.startDateTime <= :endTime AND b.endDateTime >= :startTime)")
    List<BlockedTimeSlot> findOverlappingSlots(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
    
    @Query("SELECT b FROM BlockedTimeSlot b WHERE " +
           "b.startDateTime >= :startDate AND b.endDateTime <= :endDate " +
           "ORDER BY b.startDateTime")
    List<BlockedTimeSlot> findByDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
}
