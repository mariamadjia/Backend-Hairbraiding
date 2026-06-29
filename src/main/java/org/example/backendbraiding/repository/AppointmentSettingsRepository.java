package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.AppointmentSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppointmentSettingsRepository extends JpaRepository<AppointmentSettings, Long> {
    Optional<AppointmentSettings> findFirstByOrderByIdDesc();
}
