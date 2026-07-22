package org.example.backendbraiding.service;

import org.example.backendbraiding.model.ServiceItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AppointmentTextureTests {
    @Test
    void returnsCanonicalConfiguredTexture() {
        ServiceItem service = new ServiceItem();
        service.setHairTextures(List.of("Deep Wave", "Body Wave"));

        assertEquals("Deep Wave", AppointmentService.resolveTexture(service, " deep wave "));
    }

    @Test
    void requiresTextureWhenServiceOffersChoices() {
        ServiceItem service = new ServiceItem();
        service.setHairTextures(List.of("Deep Wave"));

        assertThrows(IllegalArgumentException.class, () -> AppointmentService.resolveTexture(service, null));
        assertThrows(IllegalArgumentException.class, () -> AppointmentService.resolveTexture(service, "Straight"));
    }
}
