package org.example.backendbraiding.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class PublicBookingRateLimitFilterTests {
    @Test
    void limitsPublicAppointmentWritesByClientAndEndpoint() throws Exception {
        PublicBookingRateLimitFilter filter = new PublicBookingRateLimitFilter();
        ReflectionTestUtils.setField(filter, "requestsPerMinute", 1);
        AtomicBoolean invoked = new AtomicBoolean();
        FilterChain chain = (request, response) -> invoked.set(true);

        MockHttpServletRequest first = new MockHttpServletRequest("POST", "/api/appointments");
        first.setRemoteAddr("203.0.113.10");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(first, firstResponse, chain);
        assertTrue(invoked.get());

        MockHttpServletRequest second = new MockHttpServletRequest("POST", "/api/appointments");
        second.setRemoteAddr("203.0.113.10");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(second, secondResponse, chain);
        assertEquals(429, secondResponse.getStatus());
        assertTrue(secondResponse.getContentAsString().contains("Too many booking requests"));
    }
}
