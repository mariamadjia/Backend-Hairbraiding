package org.example.backendbraiding.service;

import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.dto.BookingQuoteRequest;
import org.example.backendbraiding.dto.BookingQuoteResponse;
import org.example.backendbraiding.model.AppointmentSettings;
import org.example.backendbraiding.model.LengthOption;
import org.example.backendbraiding.model.ServiceItem;
import org.example.backendbraiding.repository.AppointmentSettingsRepository;
import org.example.backendbraiding.repository.ServiceItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingQuoteService {
    private final ServiceItemRepository serviceItemRepository;
    private final AppointmentSettingsRepository settingsRepository;
    private final BookingQuoteTokenService tokenService;

    @Transactional(readOnly = true)
    public BookingQuoteResponse quote(BookingQuoteRequest request) {
        ServiceItem service = serviceItemRepository.findByIdAndActiveTrue(request.getServiceId())
                .orElseThrow(() -> new IllegalArgumentException("Service is unavailable"));
        LengthOption option = resolveOption(service, request.getLengthOptionId());
        String foundation = normalizeFoundation(service, request.getFoundation());
        String price = option == null ? service.getPrice()
                : "KNOTLESS".equals(foundation) && "SEPARATE".equals(service.getKnotlessPricingMode())
                    ? option.getKnotlessPrice() : option.getPrice();
        long baseCents = MoneySupport.requirePositiveCents(price, "Selected price");
        long adjustmentCents = "KNOTLESS".equals(foundation)
                && !"SEPARATE".equals(service.getKnotlessPricingMode())
                ? MoneySupport.positiveCents(service.getKnotlessPriceAdjustment()).orElse(0L) : 0L;
        long priceCents = Math.addExact(baseCents, adjustmentCents);
        long configuredDeposit = service.getDepositOverrideCents() != null
                ? service.getDepositOverrideCents()
                : settingsRepository.findFirstByOrderByIdDesc()
                    .map(AppointmentSettings::getDefaultDepositCents).orElse(5000L);
        if (configuredDeposit <= 0) throw new IllegalStateException("Booking deposit is not configured");
        long depositCents = Math.min(configuredDeposit, priceCents);
        long version = service.getVersion() == null ? 0L : service.getVersion();
        BookingQuoteTokenService.SignedQuote signed = tokenService.create(
                service.getId(), option == null ? null : option.getId(), foundation,
                priceCents, depositCents, version);
        return BookingQuoteResponse.builder()
                .serviceId(service.getId())
                .lengthOptionId(option == null ? null : option.getId())
                .servicePrice(MoneySupport.fromCents(priceCents))
                .servicePriceCents(priceCents)
                .depositCents(depositCents)
                .remainingBalanceCents(priceCents - depositCents)
                .serviceVersion(version)
                .quoteToken(signed.token())
                .expiresAt(signed.expiresAt())
                .build();
    }

    private LengthOption resolveOption(ServiceItem service, Long lengthOptionId) {
        if (service.getLengthOptions().isEmpty()) {
            if (lengthOptionId != null) throw new IllegalArgumentException("This service does not use a length option");
            return null;
        }
        if (lengthOptionId == null) throw new IllegalArgumentException("Choose a length");
        return service.getLengthOptions().stream()
                .filter(option -> lengthOptionId.equals(option.getId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Selected length is unavailable"));
    }

    private String normalizeFoundation(ServiceItem service, String requested) {
        if (!Boolean.TRUE.equals(service.getFoundationChoicesEnabled())) {
            if (requested != null && !requested.isBlank()) {
                throw new IllegalArgumentException("This service does not offer braid foundation choices");
            }
            return null;
        }
        if (requested == null || requested.isBlank()) {
            throw new IllegalArgumentException("Choose a braid foundation");
        }
        String value = requested.trim().toUpperCase();
        if (!value.equals("REGULAR") && !value.equals("KNOTLESS")) {
            throw new IllegalArgumentException("Selected braid foundation is unavailable");
        }
        return value;
    }
}
