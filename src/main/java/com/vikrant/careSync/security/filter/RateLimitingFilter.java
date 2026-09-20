package com.vikrant.careSync.security.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> otpBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> authBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> aiBuckets = new ConcurrentHashMap<>();

    private Bucket createBucket(long capacity, Duration refillDuration) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, refillDuration)
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String clientIp = getClientIP(request);

        Bucket targetBucket = null;

        if (path.contains("/api/auth/send-otp") || path.contains("/api/auth/forgot-password")) {
            // Max 5 OTP requests per minute
            targetBucket = otpBuckets.computeIfAbsent(clientIp, k -> createBucket(5, Duration.ofMinutes(1)));
        } else if (path.contains("/api/auth/login") || path.contains("/api/auth/register")) {
            // Max 10 Auth requests per minute
            targetBucket = authBuckets.computeIfAbsent(clientIp, k -> createBucket(10, Duration.ofMinutes(1)));
        } else if (path.contains("/api/ai/")) {
            // Max 20 AI queries per minute
            targetBucket = aiBuckets.computeIfAbsent(clientIp, k -> createBucket(20, Duration.ofMinutes(1)));
        }

        if (targetBucket != null) {
            if (targetBucket.tryConsume(1)) {
                filterChain.doFilter(request, response);
            } else {
                log.warn("Rate limit exceeded for IP: {} on endpoint: {}", clientIp, path);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                    "{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Please try again in a minute.\"}"
                );
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }
}
