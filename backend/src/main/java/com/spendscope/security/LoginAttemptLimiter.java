package com.spendscope.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A deliberately simple in-memory throttle on failed logins.
 *
 * <p>Keyed by {@code email + client IP} so one attacker cannot lock every user
 * out by hammering a single account from elsewhere, and one IP cannot spray
 * many accounts for free.
 *
 * <p>Limitation, stated plainly: this is per-process state. With more than one
 * backend instance behind a load balancer each instance keeps its own counters,
 * so the effective limit is multiplied by the instance count. A shared store
 * (or an edge rate limiter) is the real answer at that scale; see
 * docs/security.md.
 */
@Component
public class LoginAttemptLimiter {

    private final SecurityProperties properties;
    private final Map<String, Attempts> attemptsByKey = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(SecurityProperties properties) {
        this.properties = properties;
    }

    public boolean isBlocked(String email, String clientIp) {
        Attempts attempts = attemptsByKey.get(key(email, clientIp));
        if (attempts == null || attempts.isExpired(properties.getLoginAttemptWindow())) {
            return false;
        }
        return attempts.count.get() >= properties.getMaxLoginAttempts();
    }

    public void recordFailure(String email, String clientIp) {
        attemptsByKey.compute(key(email, clientIp), (ignored, existing) -> {
            if (existing == null || existing.isExpired(properties.getLoginAttemptWindow())) {
                return new Attempts();
            }
            existing.count.incrementAndGet();
            return existing;
        });
        evictExpired();
    }

    public void recordSuccess(String email, String clientIp) {
        attemptsByKey.remove(key(email, clientIp));
    }

    private String key(String email, String clientIp) {
        return (email == null ? "" : email.toLowerCase()) + "|" + clientIp;
    }

    /**
     * Keeps the map from growing without bound. Cheap because it only runs on
     * failure, which is rare compared to successful traffic.
     */
    private void evictExpired() {
        Duration window = properties.getLoginAttemptWindow();
        attemptsByKey.entrySet().removeIf(entry -> entry.getValue().isExpired(window));
    }

    private static final class Attempts {
        private final AtomicInteger count = new AtomicInteger(1);
        private final Instant firstAttemptAt = Instant.now();

        private boolean isExpired(Duration window) {
            return firstAttemptAt.plus(window).isBefore(Instant.now());
        }
    }
}
