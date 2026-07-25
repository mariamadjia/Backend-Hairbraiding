package org.example.backendbraiding.service;

import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.dto.PricingDepositRequest;
import org.example.backendbraiding.model.AppointmentSettings;
import org.example.backendbraiding.model.PricingHistory;
import org.example.backendbraiding.model.ServiceItem;
import org.example.backendbraiding.repository.AppointmentSettingsRepository;
import org.example.backendbraiding.repository.PricingHistoryRepository;
import org.example.backendbraiding.repository.ServiceItemRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PricingManagementService {
    private final AppointmentSettingsRepository settingsRepository;
    private final ServiceItemRepository serviceItemRepository;
    private final PricingHistoryRepository historyRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> deposits() {
        AppointmentSettings settings = settingsRepository.findFirstByOrderByIdDesc().orElse(null);
        long defaultCents = settings == null || settings.getDefaultDepositCents() == null
                ? 5000L : settings.getDefaultDepositCents();
        List<Map<String, Object>> overrides = serviceItemRepository.findAllByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                .filter(service -> service.getDepositOverrideCents() != null)
                .map(service -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("serviceId", service.getId());
                    entry.put("serviceName", service.getName());
                    entry.put("depositCents", service.getDepositOverrideCents());
                    return entry;
                }).toList();
        return Map.of("defaultDepositCents", defaultCents, "overrides", overrides);
    }

    @Transactional
    public Map<String, Object> updateDeposits(PricingDepositRequest request) {
        AppointmentSettings settings = settingsRepository.findFirstByOrderByIdDesc()
                .orElseGet(() -> settingsRepository.save(new AppointmentSettings()));
        long previousDefault = settings.getDefaultDepositCents() == null ? 5000L : settings.getDefaultDepositCents();
        settings.setDefaultDepositCents(request.getDefaultDepositCents());
        settings.setUpdatedAt(LocalDateTime.now());
        settingsRepository.save(settings);

        Map<Long, Long> requested = new LinkedHashMap<>();
        request.getOverrides().forEach(override -> requested.put(override.getServiceId(), override.getDepositCents()));
        List<ServiceItem> services = serviceItemRepository.findAllByActiveTrueOrderByDisplayOrderAscIdAsc();
        for (ServiceItem service : services) {
            Long before = service.getDepositOverrideCents();
            Long after = requested.get(service.getId());
            service.setDepositOverrideCents(after);
            if (!java.util.Objects.equals(before, after)) {
                record(service, "DEPOSIT_UPDATED", "Deposit override changed from " + display(before) + " to " + display(after));
            }
        }
        serviceItemRepository.saveAll(services);
        if (previousDefault != request.getDefaultDepositCents()) {
            record(null, "DEFAULT_DEPOSIT_UPDATED", "Default deposit changed from " + display(previousDefault) + " to " + display(request.getDefaultDepositCents()));
        }
        return deposits();
    }

    @Transactional(readOnly = true)
    public List<PricingHistory> history(int limit) {
        return historyRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.min(Math.max(limit, 1), 200)));
    }

    public void record(ServiceItem service, String action, String summary) {
        PricingHistory entry = new PricingHistory();
        entry.setServiceItemId(service == null ? null : service.getId());
        entry.setServiceName(service == null ? "All services" : service.getName());
        entry.setAction(action);
        entry.setSummary(summary.length() > 1000 ? summary.substring(0, 997) + "..." : summary);
        historyRepository.save(entry);
    }

    private String display(Long cents) {
        return cents == null ? "default" : "$" + String.format("%.2f", cents / 100.0);
    }
}
