package org.example.backendbraiding.service;

import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.model.StripeWebhookEvent;
import org.example.backendbraiding.repository.StripeWebhookEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class StripeWebhookEventService {
    private final StripeWebhookEventRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean begin(String eventId, String eventType) {
        StripeWebhookEvent event = repository.findByStripeEventId(eventId).orElse(null);
        if (event != null) {
            if (event.getStatus() == StripeWebhookEvent.Status.PROCESSED) return false;
            if (event.getStatus() == StripeWebhookEvent.Status.PROCESSING
                    && event.getUpdatedAt() != null
                    && Duration.between(event.getUpdatedAt(), LocalDateTime.now()).toMinutes() < 5) return false;
            event.setStatus(StripeWebhookEvent.Status.PROCESSING);
            event.setAttempts(event.getAttempts() + 1);
            event.setLastError(null);
            repository.save(event);
            return true;
        }
        event = new StripeWebhookEvent();
        event.setStripeEventId(eventId);
        event.setEventType(eventType);
        event.setStatus(StripeWebhookEvent.Status.PROCESSING);
        try {
            repository.saveAndFlush(event);
            return true;
        } catch (DataIntegrityViolationException concurrentDelivery) {
            return false;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processed(String eventId) {
        StripeWebhookEvent event = repository.findByStripeEventId(eventId).orElseThrow();
        event.setStatus(StripeWebhookEvent.Status.PROCESSED);
        event.setProcessedAt(LocalDateTime.now());
        repository.save(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(String eventId, String error) {
        repository.findByStripeEventId(eventId).ifPresent(event -> {
            event.setStatus(StripeWebhookEvent.Status.FAILED);
            event.setLastError(error == null ? "Unknown processing failure" : error.substring(0, Math.min(error.length(), 1000)));
            repository.save(event);
        });
    }
}
