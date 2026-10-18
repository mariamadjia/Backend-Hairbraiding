package org.example.backendbraiding.controller;

import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.dto.NotificationAdminDTO;
import org.example.backendbraiding.model.NotificationOutbox;
import org.example.backendbraiding.repository.NotificationOutboxRepository;
import org.example.backendbraiding.service.NotificationOutboxClaimService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@RestController
@RequestMapping("/api/admin/notifications")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class NotificationAdminController {
    private final NotificationOutboxRepository repository;
    private final NotificationOutboxClaimService claimService;

    @GetMapping
    public Page<NotificationAdminDTO> list(
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "ALL") String channel,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Specification<NotificationOutbox> spec = (root, ignored, cb) -> cb.conjunction();
        if (!"ALL".equalsIgnoreCase(status)) {
            NotificationOutbox.Status parsed = NotificationOutbox.Status.valueOf(status.toUpperCase(Locale.ROOT));
            spec = spec.and((root, ignored, cb) -> cb.equal(root.get("status"), parsed));
        }
        if (!"ALL".equalsIgnoreCase(channel)) {
            NotificationOutbox.Channel parsed = NotificationOutbox.Channel.valueOf(channel.toUpperCase(Locale.ROOT));
            spec = spec.and((root, ignored, cb) -> cb.equal(root.get("channel"), parsed));
        }
        if (query != null && !query.isBlank()) {
            String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, ignored, cb) -> cb.or(
                    cb.like(cb.lower(root.get("recipient")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.get("subject"), "")), pattern),
                    cb.like(cb.lower(root.join("appointment").join("customer").get("firstName")), pattern),
                    cb.like(cb.lower(root.join("appointment").join("customer").get("lastName")), pattern)));
        }
        return repository.findAll(spec, PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)),
                        Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(this::dto);
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<NotificationAdminDTO> retry(@PathVariable Long id) {
        return ResponseEntity.ok(dto(claimService.retry(id)));
    }

    private NotificationAdminDTO dto(NotificationOutbox item) {
        var customer = item.getAppointment().getCustomer();
        return new NotificationAdminDTO(item.getId(), item.getAppointment().getId(),
                customer.getFirstName() + " " + customer.getLastName(), item.getChannel().name(), item.getRecipient(),
                item.getSubject(), item.getStatus().name(), item.getAttempts(), item.getLastError(),
                item.getCreatedAt(), item.getUpdatedAt());
    }
}
