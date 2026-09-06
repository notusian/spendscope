package com.spendscope.demo;

import com.spendscope.analytics.PeriodResolver;
import com.spendscope.audit.AuditEventType;
import com.spendscope.audit.AuditService;
import com.spendscope.auth.AuthService;
import com.spendscope.common.error.ApiException;
import com.spendscope.common.error.ErrorCode;
import com.spendscope.dashboard.DashboardService;
import com.spendscope.user.User;
import com.spendscope.user.UserRepository;
import com.spendscope.user.UserResponse;
import com.spendscope.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The "Try demo" flow.
 *
 * <p>Creates a throwaway account, fills it with synthetic data and logs the
 * visitor straight in. No email, no password, nothing to remember.
 *
 * <p>Because this is the one endpoint an anonymous caller can use to create
 * rows, it is rate limited per IP and the accounts it creates are reaped on a
 * schedule.
 */
@Service
public class DemoService {

    private static final Logger log = LoggerFactory.getLogger(DemoService.class);

    private static final String DEMO_EMAIL_DOMAIN = "demo.spendscope.local";
    private static final int GENERATED_PASSWORD_BYTES = 24;

    private final UserService userService;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final DashboardService dashboardService;
    private final DemoDataGenerator demoDataGenerator;
    private final AuditService auditService;
    private final PeriodResolver periodResolver;
    private final DemoProperties properties;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Attempts> sessionsByIp = new ConcurrentHashMap<>();

    public DemoService(UserService userService,
                       UserRepository userRepository,
                       AuthService authService,
                       DashboardService dashboardService,
                       DemoDataGenerator demoDataGenerator,
                       AuditService auditService,
                       PeriodResolver periodResolver,
                       DemoProperties properties) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.authService = authService;
        this.dashboardService = dashboardService;
        this.demoDataGenerator = demoDataGenerator;
        this.auditService = auditService;
        this.periodResolver = periodResolver;
        this.properties = properties;
    }

    @Transactional
    public UserResponse createSession(HttpServletRequest request, HttpServletResponse response) {
        if (!properties.isEnabled()) {
            throw new DemoUnavailableException("The demo is not available");
        }
        recordAndCheckRateLimit(clientIpOf(request));

        // A random local email and a random password nobody is ever told. The
        // account is only reachable through the session this request creates.
        String email = "demo-" + UUID.randomUUID() + "@" + DEMO_EMAIL_DOMAIN;
        User user = userService.create(email, generatePassword(), true);

        dashboardService.createDefaultWidgets(user.getId());
        demoDataGenerator.populate(user.getId(), periodResolver.today());

        authService.loginAs(user, request, response);
        auditService.record(user.getId(), AuditEventType.DEMO_SESSION_CREATED);

        log.info("Created demo session userId={}", user.getId());
        return UserResponse.from(user);
    }

    /**
     * Deletes demo accounts past their retention window.
     *
     * <p>Everything they own goes with them through {@code ON DELETE CASCADE},
     * which is exactly why cascading deletes are the right choice for
     * {@code user_id} and the wrong one for archiving an account.
     */
    @Scheduled(fixedDelayString = "PT1H")
    @Transactional
    public void deleteExpiredDemoAccounts() {
        Instant cutoff = Instant.now().minus(properties.getRetention());
        List<User> expired = userRepository.findByDemoTrueAndCreatedAtBefore(cutoff);
        if (expired.isEmpty()) {
            return;
        }
        userRepository.deleteAll(expired);
        log.info("Deleted {} expired demo accounts", expired.size());
    }

    private String generatePassword() {
        byte[] bytes = new byte[GENERATED_PASSWORD_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void recordAndCheckRateLimit(String clientIp) {
        Attempts attempts = sessionsByIp.compute(clientIp, (ignored, existing) -> {
            if (existing == null || existing.isExpired()) {
                return new Attempts();
            }
            existing.count.incrementAndGet();
            return existing;
        });
        sessionsByIp.entrySet().removeIf(entry -> entry.getValue().isExpired());

        if (attempts.count.get() > properties.getMaxSessionsPerIp()) {
            throw new DemoUnavailableException(
                    "Too many demo sessions from this address. Please try again later.");
        }
    }

    private String clientIpOf(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null ? "unknown" : remoteAddress;
    }

    private final class Attempts {
        private final AtomicInteger count = new AtomicInteger(1);
        private final Instant firstAt = Instant.now();

        private boolean isExpired() {
            return firstAt.plus(properties.getRateLimitWindow()).isBefore(Instant.now());
        }
    }

    /** 429: the caller may succeed later, so this is not a 403. */
    public static class DemoUnavailableException extends ApiException {
        public DemoUnavailableException(String message) {
            super(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED, message);
        }
    }
}
