package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.AppointmentSettings;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppointmentSettingsRepository extends JpaRepository<AppointmentSettings, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AppointmentSettings> findFirstByOrderByIdDesc();
}
