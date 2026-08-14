package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.HomepageSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface HomepageSettingsRepository extends JpaRepository<HomepageSettings, Long> {
    Optional<HomepageSettings> findFirstByOrderByIdAsc();
}
