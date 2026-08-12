package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.Admin;
import org.example.backendbraiding.model.AdminPasswordToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminPasswordTokenRepository extends JpaRepository<AdminPasswordToken, Long> {
    Optional<AdminPasswordToken> findByTokenHashAndPurpose(String tokenHash, String purpose);
    void deleteByAdminAndPurposeAndUsedAtIsNull(Admin admin, String purpose);
}
