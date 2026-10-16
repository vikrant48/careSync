package com.vikrant.careSync.security.filter;

import com.vikrant.careSync.security.ClientIpResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimitingFilterTest {

    private int status(RateLimitingFilter filter, String path, String forwardedFor) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", forwardedFor);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    void rotatingTheSpoofableHeaderPartDoesNotResetTheLimit() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(new ClientIpResolver(1));
        for (int i = 0; i < 5; i++) {
            // Left part is attacker-controlled; right-most entry comes from our proxy and stays the same
            assertEquals(200, status(filter, "/api/auth/verify-otp", "198.51.100." + i + ", 203.0.113.7"));
        }
        assertEquals(429, status(filter, "/api/auth/verify-otp", "198.51.100.99, 203.0.113.7"));
    }

    @Test
    void otpVerificationAndResetEndpointsAreLimited() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(new ClientIpResolver(1));
        for (int i = 0; i < 5; i++) {
            status(filter, "/api/auth/reset-password-otp", "203.0.113.8");
        }
        assertEquals(429, status(filter, "/api/auth/reset-password-otp", "203.0.113.8"));
    }

    @Test
    void idleBucketsAreEvicted() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(new ClientIpResolver(1));
        status(filter, "/api/auth/login", "203.0.113.9");
        assertEquals(1, filter.trackedClients());
        filter.evictIdleBuckets(); // still fresh
        assertEquals(1, filter.trackedClients());
    }
}
