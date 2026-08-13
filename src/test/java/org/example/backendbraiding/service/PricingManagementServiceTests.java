package org.example.backendbraiding.service;

import org.example.backendbraiding.dto.PricingBatchRequest;
import org.example.backendbraiding.model.LengthOption;
import org.example.backendbraiding.model.ServiceItem;
import org.example.backendbraiding.repository.AppointmentSettingsRepository;
import org.example.backendbraiding.repository.PricingHistoryRepository;
import org.example.backendbraiding.repository.ServiceItemRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PricingManagementServiceTests {

    @Test
    void updatesRegularAndSeparateKnotlessPricesTogether() {
        Fixture fixture = fixture("250.00");
        PricingBatchRequest request = request(30000L, 35000L);

        fixture.service().updatePrices(request);

        assertEquals("300", fixture.option().getPrice());
        assertEquals("350", fixture.option().getKnotlessPrice());
    }

    @Test
    void rejectsSeparatePricingWhenKnotlessPriceIsMissing() {
        Fixture fixture = fixture("250.00");
        PricingBatchRequest request = request(30000L, null);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> fixture.service().updatePrices(request));

        assertEquals("Enter the Knotless Waist price", error.getMessage());
    }

    @Test
    void rejectsExistingSeparatePriceAbovePublishedLimit() {
        Fixture fixture = fixture("10000.01");
        PricingBatchRequest request = request(30000L, 1000001L);

        assertThrows(IllegalArgumentException.class, () -> fixture.service().updatePrices(request));
    }

    private Fixture fixture(String knotlessPrice) {
        AppointmentSettingsRepository settings = mock(AppointmentSettingsRepository.class);
        ServiceItemRepository services = mock(ServiceItemRepository.class);
        PricingHistoryRepository history = mock(PricingHistoryRepository.class);
        PricingManagementService pricing = new PricingManagementService(settings, services, history);

        ServiceItem item = new ServiceItem();
        item.setId(12L);
        item.setVersion(3L);
        item.setName("Small");
        item.setActive(true);
        item.setFoundationChoicesEnabled(true);
        item.setKnotlessPricingMode("SEPARATE");
        LengthOption option = new LengthOption();
        option.setId(42L);
        option.setName("Waist");
        option.setPrice("250.00");
        option.setKnotlessPrice(knotlessPrice);
        option.setDisplayOrder(0);
        option.setServiceItem(item);
        item.setLengthOptions(new java.util.ArrayList<>(List.of(option)));

        when(services.findByIdAndActiveTrue(12L)).thenReturn(Optional.of(item));
        when(services.save(any(ServiceItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return new Fixture(pricing, option);
    }

    private PricingBatchRequest request(Long regularCents, Long knotlessCents) {
        PricingBatchRequest.LengthPriceChange length = new PricingBatchRequest.LengthPriceChange();
        length.setLengthOptionId(42L);
        length.setPriceCents(regularCents);
        length.setKnotlessPriceCents(knotlessCents);
        length.setDisplayOrder(0);
        PricingBatchRequest.ServicePriceChange service = new PricingBatchRequest.ServicePriceChange();
        service.setServiceId(12L);
        service.setVersion(3L);
        service.setLengths(List.of(length));
        PricingBatchRequest request = new PricingBatchRequest();
        request.setChanges(List.of(service));
        return request;
    }

    private record Fixture(PricingManagementService service, LengthOption option) {}
}
