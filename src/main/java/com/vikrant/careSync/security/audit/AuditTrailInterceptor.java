package com.vikrant.careSync.security.audit;

import com.vikrant.careSync.security.ClientIpResolver;
import com.vikrant.careSync.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Audit trail for every request to an endpoint that serves or changes patient data, whatever the outcome
 * (including refused ones). Records the route template (e.g. "GET /api/patients/{id}"), numeric record ids from
 * the path, the HTTP status, the user and the client IP. Never records bodies, query strings or text from the path
 * (an illness keyword or username in a URL stays out of the log).
 */
@Component
@RequiredArgsConstructor
public class AuditTrailInterceptor implements HandlerInterceptor {

    /** URL prefixes whose endpoints read or change patient data, plus admin actions. */
    static final List<String> AUDITED_PREFIXES = List.of(
            "/api/patients", "/api/medical-history", "/api/appointments", "/api/files", "/api/ai",
            "/api/reports", "/api/analytics", "/api/bookings", "/api/payments", "/api/feedback",
            "/api/vitals", "/api/admin", "/api/seed");

    private final AuditService auditService;
    private final ClientIpResolver clientIpResolver;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex) {
        String path = request.getRequestURI();
        if (AUDITED_PREFIXES.stream().noneMatch(path::startsWith) || "OPTIONS".equals(request.getMethod())) {
            return;
        }
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route = request.getMethod() + " " + (pattern != null ? pattern : "(unmatched route)");
        int status = ex != null && response.getStatus() < 400 ? 500 : response.getStatus();

        auditService.log(currentUser(), action(request.getMethod(), status), route, recordIds(request),
                "status=" + status, clientIpResolver.resolve(request));
    }

    static String action(String method, int status) {
        String base = switch (method) {
            case "GET", "HEAD" -> "READ";
            case "POST" -> "CREATE";
            case "PUT", "PATCH" -> "UPDATE";
            case "DELETE" -> "DELETE";
            default -> method;
        };
        if (status == 401 || status == 403) {
            return base + "_DENIED";
        }
        return status >= 400 ? base + "_FAILED" : base;
    }

    /** Only numeric path variables (ids); text variables could be patient data. */
    @SuppressWarnings("unchecked")
    static String recordIds(HttpServletRequest request) {
        Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(vars instanceof Map<?, ?> map) || map.isEmpty()) {
            return "N/A";
        }
        String ids = new TreeMap<>((Map<String, String>) map).entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue().matches("\\d{1,19}"))
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(","));
        return ids.isEmpty() ? "N/A" : ids;
    }

    private static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return "anonymous";
        }
        return auth.getName();
    }
}
