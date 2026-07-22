package org.example.backendbraiding.service;

import org.example.backendbraiding.model.ServiceItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppointmentFoundationTests {
    @Test
    void disabledFoundationKeepsExistingBookingFlow() {
        ServiceItem service = new ServiceItem();
        service.setFoundationChoicesEnabled(false);

        assertNull(AppointmentService.resolveFoundation(service, null));
        assertThrows(IllegalArgumentException.class,
                () -> AppointmentService.resolveFoundation(service, "KNOTLESS"));
    }

    @Test
    void enabledFoundationRequiresAValidChoice() {
        ServiceItem service = new ServiceItem();
        service.setFoundationChoicesEnabled(true);

        assertEquals("REGULAR", AppointmentService.resolveFoundation(service, "regular"));
        assertEquals("KNOTLESS", AppointmentService.resolveFoundation(service, " knotless "));
        assertThrows(IllegalArgumentException.class,
                () -> AppointmentService.resolveFoundation(service, null));
    }

    @Test
    void knotlessAdjustmentIsCalculatedByTheBackend() {
        ServiceItem service = new ServiceItem();
        service.setKnotlessPriceAdjustment("40");

        assertEquals("280", AppointmentService.priceForFoundation("280", service, "REGULAR"));
        assertEquals("320", AppointmentService.priceForFoundation("280", service, "KNOTLESS"));
    }
}
