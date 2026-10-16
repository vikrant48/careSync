package com.vikrant.careSync.security.filter;

import com.vikrant.careSync.security.ClientIpResolver;
import com.vikrant.careSync.security.service.SecurityService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityFilter extends OncePerRequestFilter {

    private final SecurityService securityService;
    private final ClientIpResolver clientIpResolver;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String requestURI = request.getRequestURI();

        // Skip IP blocking for admin and auth endpoints (login enforces its own IP block check)
        if (requestURI.startsWith("/api/admin/") || requestURI.startsWith("/api/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Localhost clients are exempt inside SecurityService.isIPBlocked (dev convenience). The check uses the
        // resolved client IP, not the TCP peer, so a reverse proxy on the same host does not exempt everyone.
        String clientIP = clientIpResolver.resolve(request);
        log.debug("SecurityFilter - clientIP={}, remoteAddr={}", clientIP, request.getRemoteAddr());

        if (securityService.isIPBlocked(clientIP)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.getWriter().write("Access denied: IP address is blocked");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
