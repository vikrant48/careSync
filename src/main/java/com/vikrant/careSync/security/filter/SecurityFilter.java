package com.vikrant.careSync.security.filter;

import com.vikrant.careSync.security.service.SecurityService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class SecurityFilter extends OncePerRequestFilter {

    private final SecurityService securityService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        
        String requestURI = request.getRequestURI();
        
        // Skip IP blocking for admin and auth endpoints
        if (requestURI.startsWith("/api/admin/") || requestURI.startsWith("/api/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Skip IP blocking for localhost in development
        String clientIP = getClientIPAddress(request);
        if ("127.0.0.1".equals(clientIP) || "0:0:0:0:0:0:0:1".equals(clientIP) || "::1".equals(clientIP)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Check if IP is blocked
        if (securityService.isIPBlocked(clientIP)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.getWriter().write("Access denied: IP address is blocked");
            return;
        }
        
        filterChain.doFilter(request, response);
    }

    private String getClientIPAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty() && !"unknown".equalsIgnoreCase(xRealIP)) {
            return xRealIP;
        }
        
        return request.getRemoteAddr();
    }
}