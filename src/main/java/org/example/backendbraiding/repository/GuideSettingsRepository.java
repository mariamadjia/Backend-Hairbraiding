package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.GuideSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface GuideSettingsRepository extends JpaRepository<GuideSettings, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select settings from GuideSettings settings where settings.id = :id")
    Optional<GuideSettings> findByIdForUpdate(@Param("id") Long id);
}
