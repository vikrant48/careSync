package com.vikrant.careSync.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the real client IP for rate limiting, IP blocking, login auditing and session records.
 *
 * Each reverse proxy appends the address it received the request from to X-Forwarded-For, so only
 * the right-most entries were written by proxies we control. Everything to the left is supplied by
 * the client and can be forged. With N trusted proxy hops the client address is the N-th entry from
 * the right. With 0 hops the header is ignored and the TCP peer address is used.
 *
 * Set app.security.trusted-proxy-hops to the number of proxies in front of the app:
 * 0 = exposed directly, 1 = one load balancer / platform router (default), 2 = CDN + load balancer.
 */
@Component
public class ClientIpResolver {

    private final int trustedProxyHops;

    public ClientIpResolver(@Value("${app.security.trusted-proxy-hops:1}") int trustedProxyHops) {
        this.trustedProxyHops = Math.max(0, trustedProxyHops);
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (trustedProxyHops == 0) {
            return remoteAddr;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddr;
        }
        String[] hops = forwardedFor.split(",");
        int index = hops.length - trustedProxyHops;
        if (index < 0) {
            // Fewer entries than trusted proxies: the request did not pass through all of them
            return remoteAddr;
        }
        String candidate = hops[index].trim();
        return candidate.isEmpty() || "unknown".equalsIgnoreCase(candidate) ? remoteAddr : candidate;
    }
}
