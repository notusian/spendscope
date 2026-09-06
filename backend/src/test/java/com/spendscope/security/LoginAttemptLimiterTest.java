package com.spendscope.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptLimiterTest {

    private static final String EMAIL = "alice@example.com";
    private static final String IP = "203.0.113.7";

    private SecurityProperties properties;
    private LoginAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        properties = new SecurityProperties();
        properties.setMaxLoginAttempts(3);
        properties.setLoginAttemptWindow(Duration.ofMinutes(15));
        limiter = new LoginAttemptLimiter(properties);
    }

    @Test
    @DisplayName("allows attempts below the limit")
    void allowsBelowLimit() {
        limiter.recordFailure(EMAIL, IP);
        limiter.recordFailure(EMAIL, IP);
        assertThat(limiter.isBlocked(EMAIL, IP)).isFalse();
    }

    @Test
    @DisplayName("blocks once the limit is reached")
    void blocksAtLimit() {
        for (int attempt = 0; attempt < 3; attempt++) {
            limiter.recordFailure(EMAIL, IP);
        }
        assertThat(limiter.isBlocked(EMAIL, IP)).isTrue();
    }

    @Test
    @DisplayName("a successful login clears the counter")
    void successResetsCounter() {
        limiter.recordFailure(EMAIL, IP);
        limiter.recordFailure(EMAIL, IP);
        limiter.recordSuccess(EMAIL, IP);
        limiter.recordFailure(EMAIL, IP);

        assertThat(limiter.isBlocked(EMAIL, IP)).isFalse();
    }

    @Test
    @DisplayName("one attacker cannot lock a victim out from a different address")
    void keyedByEmailAndIp() {
        // Keying on email alone would let anyone lock any account out by
        // failing three logins against it.
        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.recordFailure(EMAIL, "198.51.100.9");
        }

        assertThat(limiter.isBlocked(EMAIL, "198.51.100.9")).isTrue();
        assertThat(limiter.isBlocked(EMAIL, IP)).isFalse();
    }

    @Test
    @DisplayName("one address spraying many accounts is counted per account")
    void differentEmailsAreSeparate() {
        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.recordFailure(EMAIL, IP);
        }
        assertThat(limiter.isBlocked("bob@example.com", IP)).isFalse();
    }

    @Test
    @DisplayName("the counter expires with the window")
    void counterExpires() {
        properties.setLoginAttemptWindow(Duration.ofMillis(1));
        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.recordFailure(EMAIL, IP);
        }

        await(5);
        assertThat(limiter.isBlocked(EMAIL, IP)).isFalse();
    }

    @Test
    @DisplayName("email comparison is case-insensitive")
    void emailIsCaseInsensitive() {
        for (int attempt = 0; attempt < 3; attempt++) {
            limiter.recordFailure("Alice@Example.com", IP);
        }
        assertThat(limiter.isBlocked("alice@example.com", IP)).isTrue();
    }

    private void await(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
