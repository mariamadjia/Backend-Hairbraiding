package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {
    List<TimeSlot> findByDayOfWeekOrderBySlotOrderAsc(String dayOfWeek);
    void deleteByDayOfWeek(String dayOfWeek);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM TimeSlot t WHERE t.dayOfWeek IN :days")
    int deleteAllByDayOfWeekIn(@Param("days") List<String> days);
}
