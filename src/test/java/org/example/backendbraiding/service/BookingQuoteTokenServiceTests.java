package org.example.backendbraiding.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class BookingQuoteTokenServiceTests {
    private BookingQuoteTokenService service() {
        BookingQuoteTokenService service = new BookingQuoteTokenService();
        ReflectionTestUtils.setField(service, "jwtSecret",
                "test-booking-quote-secret-that-is-long-enough-for-hmac-sha-256");
        return service;
    }

    @Test
    void roundTripsAuthoritativeQuoteClaims() {
        BookingQuoteTokenService service = service();
        String token = service.create(10L, 20L, "KNOTLESS", 35500L, 5000L, 4L).token();

        BookingQuoteTokenService.QuoteClaims claims = service.parse(token);
        assertEquals(10L, claims.serviceId());
        assertEquals(20L, claims.lengthOptionId());
        assertEquals("KNOTLESS", claims.foundation());
        assertEquals(35500L, claims.priceCents());
        assertEquals(5000L, claims.depositCents());
        assertEquals(4L, claims.serviceVersion());
    }

    @Test
    void normalizesAbsentFoundationBackToNull() {
        BookingQuoteTokenService service = service();
        String token = service.create(10L, null, null, 3500L, 3500L, 0L).token();
        assertNull(service.parse(token).foundation());
    }

    @Test
    void rejectsTamperedToken() {
        BookingQuoteTokenService service = service();
        String token = service.create(10L, null, null, 3500L, 3500L, 0L).token();
        assertThrows(IllegalArgumentException.class,
                () -> service.parse(token.substring(0, token.length() - 2) + "xx"));
    }
}
