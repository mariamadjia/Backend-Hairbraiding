package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.CheckoutTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CheckoutTransactionRepository extends JpaRepository<CheckoutTransaction, Long> {
    List<CheckoutTransaction> findByCheckoutIdOrderByCreatedAtAsc(Long checkoutId);
}
