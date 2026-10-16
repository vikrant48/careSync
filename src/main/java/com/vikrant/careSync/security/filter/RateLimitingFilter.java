package com.vikrant.careSync.security.filter;

import com.vikrant.careSync.security.ClientIpResolver;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-client-IP rate limits for sensitive endpoints. Buckets are evicted after 10 idle minutes and each
 * map is capped, so a flood of distinct IPs cannot exhaust memory. Per-account OTP attempt limits live in
 * OtpAttemptLimiter, because IP limits alone can be spread across many addresses.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    static final int MAX_TRACKED_CLIENTS = 100_000;
    private static final long IDLE_EVICTION_MS = Duration.ofMinutes(10).toMillis();

    private static final List<String> OTP_PATHS = List.of(
            "/api/auth/send-otp", "/api/auth/forgot-password", "/api/auth/email-verification",
            "/api/auth/verify-otp", "/api/auth/reset-password");
    private static final List<String> AUTH_PATHS = List.of(
            "/api/auth/login", "/api/auth/register", "/api/auth/google", "/api/auth/refresh",
            "/api/auth/check-availability");

    private final ClientIpResolver clientIpResolver;

    private final Map<String, TimedBucket> otpBuckets = new ConcurrentHashMap<>();
    private final Map<String, TimedBucket> authBuckets = new ConcurrentHashMap<>();
    private final Map<String, TimedBucket> aiBuckets = new ConcurrentHashMap<>();

    private static final class TimedBucket {
        private final Bucket bucket;
        private volatile long lastAccess = System.currentTimeMillis();

        private TimedBucket(Bucket bucket) {
            this.bucket = bucket;
        }

        private boolean tryConsume() {
            lastAccess = System.currentTimeMillis();
            return bucket.tryConsume(1);
        }
    }

    private Bucket createBucket(long capacity, Duration refillDuration) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, refillDuration)
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private TimedBucket bucketFor(Map<String, TimedBucket> buckets, String clientIp, long capacity) {
        if (buckets.size() >= MAX_TRACKED_CLIENTS && !buckets.containsKey(clientIp)) {
            evictIdle(buckets);
            if (buckets.size() >= MAX_TRACKED_CLIENTS) {
                log.warn("Rate limit table full ({} clients); resetting it", buckets.size());
                buckets.clear();
            }
        }
        return buckets.computeIfAbsent(clientIp, k -> new TimedBucket(createBucket(capacity, Duration.ofMinutes(1))));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String clientIp = clientIpResolver.resolve(request);

        TimedBucket targetBucket = null;

        if (OTP_PATHS.stream().anyMatch(path::startsWith)) {
            // Max 5 OTP requests per minute
            targetBucket = bucketFor(otpBuckets, clientIp, 5);
        } else if (AUTH_PATHS.stream().anyMatch(path::startsWith)) {
            // Max 10 Auth requests per minute
            targetBucket = bucketFor(authBuckets, clientIp, 10);
        } else if (path.startsWith("/api/ai/")) {
            // Max 20 AI queries per minute
            targetBucket = bucketFor(aiBuckets, clientIp, 20);
        }

        if (targetBucket != null) {
            if (targetBucket.tryConsume()) {
                filterChain.doFilter(request, response);
            } else {
                log.warn("Rate limit exceeded for IP: {} on endpoint: {}", clientIp, path);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                        "{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Please try again in a minute.\"}");
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }

    @Scheduled(fixedDelay = 300_000)
    public void evictIdleBuckets() {
        evictIdle(otpBuckets);
        evictIdle(authBuckets);
        evictIdle(aiBuckets);
    }

    private void evictIdle(Map<String, TimedBucket> buckets) {
        long cutoff = System.currentTimeMillis() - IDLE_EVICTION_MS;
        buckets.values().removeIf(b -> b.lastAccess < cutoff);
    }

    int trackedClients() {
        return otpBuckets.size() + authBuckets.size() + aiBuckets.size();
    }
}
