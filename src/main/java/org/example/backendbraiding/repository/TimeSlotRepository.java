package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {
    List<TimeSlot> findByDayOfWeekOrderBySlotOrderAsc(String dayOfWeek);
    void deleteByDayOfWeek(String dayOfWeek);
}
