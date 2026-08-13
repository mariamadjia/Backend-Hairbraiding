package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.StripeWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StripeWebhookEventRepository extends JpaRepository<StripeWebhookEvent, Long> {
    Optional<StripeWebhookEvent> findByStripeEventId(String stripeEventId);
}
