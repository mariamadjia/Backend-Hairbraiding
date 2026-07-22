package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByEmail(String email);
    Optional<Customer> findFirstByEmailIgnoreCaseOrderByIdAsc(String email);
    Optional<Customer> findByPhoneNumber(String phoneNumber);
}
