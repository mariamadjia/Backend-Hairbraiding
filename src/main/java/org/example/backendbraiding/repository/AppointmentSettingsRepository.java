package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.AppointmentSettings;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppointmentSettingsRepository extends JpaRepository<AppointmentSettings, Long> {
    Optional<AppointmentSettings> findFirstByOrderByIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AppointmentSettings s WHERE s.id = (SELECT MAX(s2.id) FROM AppointmentSettings s2)")
    Optional<AppointmentSettings> findLatestForUpdate();
}
