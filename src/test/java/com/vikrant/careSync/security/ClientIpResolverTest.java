package com.vikrant.careSync.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {

    private MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    @Test
    void zeroHopsIgnoresForwardedHeader() {
        ClientIpResolver resolver = new ClientIpResolver(0);
        assertEquals("10.0.0.5", resolver.resolve(request("10.0.0.5", "1.2.3.4")));
    }

    @Test
    void oneHopUsesEntryAddedByOurProxyNotTheClientSuppliedOne() {
        ClientIpResolver resolver = new ClientIpResolver(1);
        // Attacker sends "X-Forwarded-For: 9.9.9.9"; our proxy appends the real address 203.0.113.7
        assertEquals("203.0.113.7", resolver.resolve(request("10.0.0.1", "9.9.9.9, 203.0.113.7")));
        assertEquals("203.0.113.7", resolver.resolve(request("10.0.0.1", "203.0.113.7")));
    }

    @Test
    void twoHopsSkipsCdnAndLoadBalancer() {
        ClientIpResolver resolver = new ClientIpResolver(2);
        assertEquals("203.0.113.7", resolver.resolve(request("10.0.0.1", "9.9.9.9, 203.0.113.7, 172.16.0.2")));
    }

    @Test
    void fallsBackToPeerWhenHeaderMissingTooShortOrBlank() {
        ClientIpResolver resolver = new ClientIpResolver(2);
        assertEquals("10.0.0.1", resolver.resolve(request("10.0.0.1", null)));
        assertEquals("10.0.0.1", resolver.resolve(request("10.0.0.1", "203.0.113.7")));
        assertEquals("10.0.0.1", new ClientIpResolver(1).resolve(request("10.0.0.1", "unknown")));
    }
}
