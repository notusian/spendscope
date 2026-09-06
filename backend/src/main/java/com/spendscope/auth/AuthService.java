package com.spendscope.auth;

import com.spendscope.audit.AuditEventType;
import com.spendscope.audit.AuditService;
import com.spendscope.common.error.ApiException;
import com.spendscope.common.error.ErrorCode;
import com.spendscope.dashboard.DashboardService;
import com.spendscope.security.AppUserDetails;
import com.spendscope.security.LoginAttemptLimiter;
import com.spendscope.user.User;
import com.spendscope.user.UserRepository;
import com.spendscope.user.UserResponse;
import com.spendscope.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Registration, login and logout.
 *
 * <p>Login is implemented explicitly rather than with Spring Security's form
 * login filter so that it can return JSON, rotate the session id, drive the
 * failed-attempt throttle and write audit rows in one readable method.
 */
@Service
public class AuthService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final DashboardService dashboardService;
    private final AuditService auditService;

    public AuthService(UserService userService,
                       UserRepository userRepository,
                       AuthenticationManager authenticationManager,
                       SecurityContextRepository securityContextRepository,
                       SessionAuthenticationStrategy sessionAuthenticationStrategy,
                       PasswordEncoder passwordEncoder,
                       LoginAttemptLimiter loginAttemptLimiter,
                       DashboardService dashboardService,
                       AuditService auditService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.dashboardService = dashboardService;
        this.auditService = auditService;
    }

    /**
     * Creates the user and gives them a starter dashboard so the app is not
     * empty on first login.
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        User user = userService.create(request.email(), request.password(), false);
        dashboardService.createDefaultWidgets(user.getId());
        auditService.record(user.getId(), AuditEventType.USER_REGISTERED);
        return UserResponse.from(user);
    }

    /**
     * Authenticates and establishes a session.
     *
     * <p>The order matters: throttle check, authenticate, rotate session id,
     * then save the security context. Rotating before saving is what prevents
     * session fixation - an attacker who planted a session id on the victim's
     * browser does not learn the new one.
     */
    public UserResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String email = UserService.normaliseEmail(request.email());
        String clientIp = clientIpOf(httpRequest);

        if (loginAttemptLimiter.isBlocked(email, clientIp)) {
            auditService.record(userIdOrNull(email), AuditEventType.LOGIN_BLOCKED, Map.of("email", email));
            throw new TooManyLoginAttemptsException();
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        } catch (BadCredentialsException ex) {
            loginAttemptLimiter.recordFailure(email, clientIp);
            auditService.record(userIdOrNull(email), AuditEventType.LOGIN_FAILURE, Map.of("email", email));
            throw ex;
        }

        loginAttemptLimiter.recordSuccess(email, clientIp);
        establishSession(authentication, httpRequest, httpResponse);

        AppUserDetails principal = (AppUserDetails) authentication.getPrincipal();
        auditService.record(principal.getUserId(), AuditEventType.LOGIN_SUCCESS);
        return new UserResponse(principal.getUserId(), principal.getEmail(), principal.isDemo(), null);
    }

    /**
     * Used by the demo flow, which has already created the user and therefore
     * has no password to check.
     */
    public void loginAs(User user, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        AppUserDetails principal = AppUserDetails.from(user);
        Authentication authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        establishSession(authentication, httpRequest, httpResponse);
    }

    public void logout(Long userId, HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        if (userId != null) {
            auditService.record(userId, AuditEventType.LOGOUT);
        }
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userService.require(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }
        userService.changePassword(userId, request.newPassword());
        auditService.record(userId, AuditEventType.PASSWORD_CHANGED);
    }

    private void establishSession(Authentication authentication,
                                  HttpServletRequest httpRequest,
                                  HttpServletResponse httpResponse) {
        // Rotates the session id when one already exists, and does nothing
        // when it does not. Either way the caller ends up on a session id that
        // was never seen by an unauthenticated request.
        sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
    }

    private Long userIdOrNull(String email) {
        return userRepository.findByEmail(email).map(User::getId).orElse(null);
    }

    /**
     * The direct peer address.
     *
     * <p>{@code X-Forwarded-For} is deliberately ignored: it is attacker
     * controlled unless every hop is a trusted proxy that overwrites it. When
     * deploying behind a load balancer, configure
     * {@code server.forward-headers-strategy=framework} so the servlet
     * container populates this from the trusted header instead.
     */
    private String clientIpOf(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null ? "unknown" : remoteAddress;
    }

    /** 429 rather than 401, so the client can tell the user to wait. */
    public static class TooManyLoginAttemptsException extends ApiException {
        public TooManyLoginAttemptsException() {
            super(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED,
                    "Too many failed login attempts. Please try again later.");
        }
    }
}
