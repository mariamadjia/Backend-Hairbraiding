package org.example.backendbraiding.service;

import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.dto.PricingDepositRequest;
import org.example.backendbraiding.dto.PricingBatchRequest;
import org.example.backendbraiding.dto.DefaultDepositPatchRequest;
import org.example.backendbraiding.dto.ServiceDepositPatchRequest;
import org.example.backendbraiding.dto.ClonePricingSizeRequest;
import org.example.backendbraiding.dto.AddPricingLengthRequest;
import org.example.backendbraiding.model.AppointmentSettings;
import org.example.backendbraiding.model.LengthOption;
import org.example.backendbraiding.model.PricingHistory;
import org.example.backendbraiding.model.ServiceItem;
import org.example.backendbraiding.repository.AppointmentSettingsRepository;
import org.example.backendbraiding.repository.PricingHistoryRepository;
import org.example.backendbraiding.repository.ServiceItemRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;

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
        long version = settings == null || settings.getVersion() == null ? 0L : settings.getVersion();
        return Map.of("defaultDepositCents", defaultCents, "version", version, "overrides", overrides);
    }

    @Transactional
    public Map<String, Object> updateDeposits(PricingDepositRequest request) {
        AppointmentSettings settings = settingsRepository.findLatestForUpdate()
                .orElseGet(() -> settingsRepository.save(new AppointmentSettings()));
        requireVersion(settings.getVersion(), request.getVersion(), "deposit settings");
        long previousDefault = settings.getDefaultDepositCents() == null ? 5000L : settings.getDefaultDepositCents();
        settings.setDefaultDepositCents(request.getDefaultDepositCents());
        settings.setUpdatedAt(LocalDateTime.now());
        settingsRepository.save(settings);

        Map<Long, PricingDepositRequest.ServiceOverride> requested = new LinkedHashMap<>();
        request.getOverrides().forEach(override -> {
            if (requested.put(override.getServiceId(), override) != null) {
                throw new IllegalArgumentException("A service deposit may only appear once");
            }
        });
        List<ServiceItem> services = requested.keySet().stream().map(this::activeService).toList();
        String batchId = UUID.randomUUID().toString();
        for (ServiceItem service : services) {
            PricingDepositRequest.ServiceOverride requestedOverride = requested.get(service.getId());
            requireVersion(service.getVersion(), requestedOverride.getVersion(), service.getName());
            Long before = service.getDepositOverrideCents();
            Long after = requestedOverride.getDepositCents();
            service.setDepositOverrideCents(after);
            if (!java.util.Objects.equals(before, after)) {
                record(service, "DEPOSIT_UPDATED",
                        "Deposit override changed from " + display(before) + " to " + display(after),
                        before == null ? null : String.valueOf(before),
                        after == null ? null : String.valueOf(after), batchId);
            }
        }
        serviceItemRepository.saveAll(services);
        if (previousDefault != request.getDefaultDepositCents()) {
            record(null, "DEFAULT_DEPOSIT_UPDATED",
                    "Default deposit changed from " + display(previousDefault) + " to " + display(request.getDefaultDepositCents()),
                    String.valueOf(previousDefault), String.valueOf(request.getDefaultDepositCents()), batchId);
        }
        serviceItemRepository.flush();
        settingsRepository.flush();
        return deposits();
    }

    @Transactional(readOnly = true)
    public List<PricingHistory> history(int limit) {
        return historyRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.min(Math.max(limit, 1), 200)));
    }

    @Transactional
    @CacheEvict(value = {"bookingCategories", "bookingCategory", "publicCategories", "allCategories", "galleryCards"}, allEntries = true)
    public List<ServiceItem> updatePrices(PricingBatchRequest request) {
        String batchId = UUID.randomUUID().toString();
        HashSet<Long> serviceIds = new HashSet<>();
        for (PricingBatchRequest.ServicePriceChange change : request.getChanges()) {
            if (!serviceIds.add(change.getServiceId())) {
                throw new IllegalArgumentException("A service may only appear once in a pricing update");
            }
            ServiceItem service = activeService(change.getServiceId());
            requireVersion(service.getVersion(), change.getVersion(), service.getName());
            String before = pricingSnapshot(service);

            if (change.getBasePriceCents() != null) {
                service.setPrice(MoneySupport.fromCents(change.getBasePriceCents()));
            }
            if (change.getKnotlessAdjustmentCents() != null) {
                service.setKnotlessPriceAdjustment(MoneySupport.fromNonNegativeCents(change.getKnotlessAdjustmentCents()));
            }
            if (change.getLengths() != null && !change.getLengths().isEmpty()) {
                Map<Long, LengthOption> options = service.getLengthOptions().stream()
                        .collect(java.util.stream.Collectors.toMap(LengthOption::getId, option -> option));
                if (options.size() != change.getLengths().size()) {
                    throw new IllegalArgumentException("Submit every length price for " + service.getName());
                }
                HashSet<Long> lengthIds = new HashSet<>();
                for (PricingBatchRequest.LengthPriceChange lengthChange : change.getLengths()) {
                    if (!lengthIds.add(lengthChange.getLengthOptionId())) {
                        throw new IllegalArgumentException("A length may only appear once");
                    }
                    LengthOption option = options.get(lengthChange.getLengthOptionId());
                    if (option == null) throw new IllegalArgumentException("A selected length is no longer available");
                    option.setPrice(MoneySupport.fromCents(lengthChange.getPriceCents()));
                    option.setDisplayOrder(lengthChange.getDisplayOrder());
                }
            }
            validateBookable(service);
            serviceItemRepository.save(service);
            String after = pricingSnapshot(service);
            record(service, "PRICES_UPDATED", "Pricing updated for " + service.getName(), before, after, batchId);
        }
        serviceItemRepository.flush();
        return request.getChanges().stream().map(change -> activeService(change.getServiceId())).toList();
    }

    @Transactional
    public Map<String, Object> updateDefaultDeposit(DefaultDepositPatchRequest request) {
        AppointmentSettings settings = settingsRepository.findLatestForUpdate()
                .orElseGet(() -> settingsRepository.save(new AppointmentSettings()));
        requireVersion(settings.getVersion(), request.getVersion(), "deposit settings");
        long before = settings.getDefaultDepositCents() == null ? 5000L : settings.getDefaultDepositCents();
        settings.setDefaultDepositCents(request.getDepositCents());
        settings.setUpdatedAt(LocalDateTime.now());
        settingsRepository.saveAndFlush(settings);
        record(null, "DEFAULT_DEPOSIT_UPDATED",
                "Default deposit changed from " + display(before) + " to " + display(request.getDepositCents()),
                String.valueOf(before), String.valueOf(request.getDepositCents()), UUID.randomUUID().toString());
        return deposits();
    }

    @Transactional
    public ServiceItem updateServiceDeposit(Long serviceId, ServiceDepositPatchRequest request) {
        ServiceItem service = activeService(serviceId);
        requireVersion(service.getVersion(), request.getVersion(), service.getName());
        Long before = service.getDepositOverrideCents();
        service.setDepositOverrideCents(request.getDepositCents());
        serviceItemRepository.saveAndFlush(service);
        record(service, "DEPOSIT_UPDATED",
                "Deposit override changed from " + display(before) + " to " + display(request.getDepositCents()),
                before == null ? null : String.valueOf(before),
                request.getDepositCents() == null ? null : String.valueOf(request.getDepositCents()),
                UUID.randomUUID().toString());
        return service;
    }

    @Transactional
    @CacheEvict(value = {"bookingCategories", "bookingCategory", "publicCategories", "allCategories", "galleryCards"}, allEntries = true)
    public ServiceItem cloneSize(ClonePricingSizeRequest request) {
        ServiceItem source = activeService(request.getCloneFromServiceId());
        ServiceItem clone = new ServiceItem();
        clone.setName(request.getName().trim());
        clone.setPrice(source.getPrice());
        clone.setDescription(source.getDescription());
        clone.setNotes(source.getNotes());
        clone.setImage(source.getImage());
        clone.setImages(new java.util.ArrayList<>(source.getImages()));
        clone.setSizePhotos(new java.util.ArrayList<>(source.getSizePhotos()));
        clone.setLink(source.getLink());
        clone.setObjectPosition(source.getObjectPosition());
        clone.setFoundationChoicesEnabled(source.getFoundationChoicesEnabled());
        clone.setKnotlessPriceAdjustment(source.getKnotlessPriceAdjustment());
        clone.setKnotlessPricingMode(source.getKnotlessPricingMode());
        clone.setDepositOverrideCents(source.getDepositOverrideCents());
        clone.setDisplayOrder(source.getDisplayOrder() + 1);
        clone.setActive(true);
        clone.setAvailableSizes(new java.util.ArrayList<>(source.getAvailableSizes()));
        clone.setHairTextures(new java.util.ArrayList<>(source.getHairTextures()));
        clone.setCategory(source.getCategory());
        clone.setSubcategory(source.getSubcategory());
        if (source.getLengthOptions().isEmpty()) {
            Long requestedBase = request.getPrices().get("Base price");
            if (requestedBase == null) throw new IllegalArgumentException("Enter the base price for the new size");
            clone.setPrice(MoneySupport.fromCents(requestedBase));
        }
        for (LengthOption sourceOption : source.getLengthOptions()) {
            LengthOption option = new LengthOption();
            option.setName(sourceOption.getName());
            option.setKnotlessPrice(sourceOption.getKnotlessPrice());
            Long requestedPrice = request.getPrices().get(sourceOption.getName());
            if (requestedPrice == null) {
                throw new IllegalArgumentException("Enter the " + sourceOption.getName() + " price for the new size");
            }
            option.setPrice(MoneySupport.fromCents(requestedPrice));
            option.setDisplayOrder(sourceOption.getDisplayOrder());
            option.setNotes(sourceOption.getNotes());
            option.setImageUrl(sourceOption.getImageUrl());
            option.setServiceItem(clone);
            clone.getLengthOptions().add(option);
        }
        validateBookable(clone);
        ServiceItem saved = serviceItemRepository.saveAndFlush(clone);
        record(saved, "SIZE_CLONED", "Created " + saved.getName() + " from " + source.getName(),
                null, pricingSnapshot(saved), UUID.randomUUID().toString());
        return saved;
    }

    @Transactional
    @CacheEvict(value = {"bookingCategories", "bookingCategory", "publicCategories", "allCategories", "galleryCards"}, allEntries = true)
    public List<ServiceItem> addLength(AddPricingLengthRequest request) {
        Map<Long, AddPricingLengthRequest.ServicePrice> requestedPrices =
                request.getServicePrices() == null ? Map.of() : request.getServicePrices().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                AddPricingLengthRequest.ServicePrice::getServiceId,
                                price -> price,
                                (left, right) -> {
                                    throw new IllegalArgumentException("A service may only appear once");
                                },
                                LinkedHashMap::new));
        List<Long> requestedIds = requestedPrices.isEmpty()
                ? (request.getServiceIds() == null ? List.of() : request.getServiceIds())
                : requestedPrices.keySet().stream().toList();
        if (requestedIds == null || requestedIds.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one service");
        }
        HashSet<Long> ids = new HashSet<>(requestedIds);
        if (ids.size() != requestedIds.size()) {
            throw new IllegalArgumentException("A service may only appear once");
        }
        String batchId = UUID.randomUUID().toString();
        java.util.ArrayList<ServiceItem> changed = new java.util.ArrayList<>();
        for (Long id : ids) {
            ServiceItem service = activeService(id);
            AddPricingLengthRequest.ServicePrice requestedPrice = requestedPrices.get(id);
            if (requestedPrice != null) {
                requireVersion(service.getVersion(), requestedPrice.getVersion(), service.getName());
            }
            if (service.getLengthOptions().stream().anyMatch(option -> option.getName().equalsIgnoreCase(request.getName().trim()))) {
                throw new IllegalArgumentException(request.getName() + " already exists for " + service.getName());
            }
            long priceCents;
            if (requestedPrice != null) {
                priceCents = requestedPrice.getPriceCents();
            } else if (request.getInitialPriceCents() != null) {
                priceCents = request.getInitialPriceCents();
            } else {
                LengthOption source = service.getLengthOptions().stream()
                        .filter(option -> request.getCopyFromLengthName() != null
                                && option.getName().equalsIgnoreCase(request.getCopyFromLengthName().trim()))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException(
                                "Choose an initial price or an existing length to copy"));
                priceCents = Math.addExact(MoneySupport.requirePositiveCents(source.getPrice(), "Length price"),
                        request.getAdjustmentCents() == null ? 0L : request.getAdjustmentCents());
            }
            if (priceCents <= 0) throw new IllegalArgumentException("Length price must be greater than zero");
            LengthOption option = new LengthOption();
            option.setName(request.getName().trim());
            option.setPrice(MoneySupport.fromCents(priceCents));
            if ("SEPARATE".equals(service.getKnotlessPricingMode())) {
                option.setKnotlessPrice(MoneySupport.fromCents(priceCents));
            }
            option.setDisplayOrder(service.getLengthOptions().stream()
                    .map(LengthOption::getDisplayOrder).filter(Objects::nonNull).max(Integer::compareTo).orElse(-1) + 1);
            option.setServiceItem(service);
            service.getLengthOptions().add(option);
            serviceItemRepository.save(service);
            record(service, "LENGTH_ADDED", "Added " + option.getName() + " at " + display(priceCents),
                    null, pricingSnapshot(service), batchId);
            changed.add(service);
        }
        serviceItemRepository.flush();
        return changed;
    }

    public void record(ServiceItem service, String action, String summary) {
        record(service, action, summary, null, null, null);
    }

    private void record(ServiceItem service, String action, String summary,
                        String before, String after, String batchId) {
        PricingHistory entry = new PricingHistory();
        entry.setServiceItemId(service == null ? null : service.getId());
        entry.setServiceName(service == null ? "All services" : service.getName());
        entry.setAction(action);
        entry.setSummary(summary.length() > 1000 ? summary.substring(0, 997) + "..." : summary);
        entry.setChangedBy(currentActor());
        entry.setSource("ADMIN_PRICING");
        entry.setBatchId(batchId);
        entry.setBeforeValue(before);
        entry.setAfterValue(after);
        historyRepository.save(entry);
    }

    private ServiceItem activeService(Long id) {
        return serviceItemRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new IllegalArgumentException("Service " + id + " is unavailable"));
    }

    private void requireVersion(Long actual, Long requested, String target) {
        if (!Objects.equals(actual == null ? 0L : actual, requested)) {
            throw new org.springframework.dao.OptimisticLockingFailureException(
                    target + " changed in another session. Reload before saving.");
        }
    }

    private void validateBookable(ServiceItem service) {
        if (service.getLengthOptions() == null || service.getLengthOptions().isEmpty()) {
            MoneySupport.requirePositiveCents(service.getPrice(), "Base price");
        } else {
            for (LengthOption option : service.getLengthOptions()) {
                if (option.getName() == null || option.getName().isBlank()) {
                    throw new IllegalArgumentException("Every length needs a name");
                }
                MoneySupport.requirePositiveCents(option.getPrice(), option.getName() + " price");
            }
        }
        if (Boolean.TRUE.equals(service.getFoundationChoicesEnabled())
                && "ADJUSTMENT".equals(service.getKnotlessPricingMode())) {
            try {
                if (new java.math.BigDecimal(Objects.toString(service.getKnotlessPriceAdjustment(), "0")
                        .replace("$", "").trim()).signum() < 0) {
                    throw new IllegalArgumentException("Knotless adjustment cannot be negative");
                }
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Knotless adjustment must be a valid amount");
            }
        }
    }

    private String pricingSnapshot(ServiceItem service) {
        return "{\"basePrice\":\"" + Objects.toString(service.getPrice(), "") + "\",\"knotless\":\""
                + Objects.toString(service.getKnotlessPriceAdjustment(), "") + "\",\"lengths\":["
                + service.getLengthOptions().stream()
                .map(option -> "{\"id\":" + option.getId() + ",\"name\":\""
                        + Objects.toString(option.getName(), "").replace("\"", "\\\"") + "\",\"price\":\""
                        + Objects.toString(option.getPrice(), "") + "\",\"order\":"
                        + option.getDisplayOrder() + "}")
                .collect(java.util.stream.Collectors.joining(",")) + "]}";
    }

    private String currentActor() {
        org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : authentication.getName();
    }

    private String display(Long cents) {
        return cents == null ? "default" : "$" + String.format("%.2f", cents / 100.0);
    }
}
