package com.spendscope.auth;

import com.spendscope.security.AppUserDetails;
import com.spendscope.user.UserResponse;
import com.spendscope.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints.
 *
 * <p>Thin by design: parse, delegate, map to a status code.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new account")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in and start a session")
    public UserResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest httpRequest,
                              HttpServletResponse httpResponse) {
        return authService.login(request, httpRequest, httpResponse);
    }

    @PostMapping("/logout")
    @Operation(summary = "Invalidate the current session")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AppUserDetails principal,
                                       HttpServletRequest httpRequest) {
        authService.logout(principal == null ? null : principal.getUserId(), httpRequest);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Return the currently authenticated user")
    public UserResponse me(@AuthenticationPrincipal AppUserDetails principal) {
        return UserResponse.from(userService.require(principal.getUserId()));
    }

    /**
     * Lets the SPA obtain an {@code XSRF-TOKEN} cookie before its first
     * state-changing request. The body is empty on purpose - the token travels
     * in the cookie, written by {@code CsrfCookieFilter}.
     */
    @GetMapping("/csrf")
    @Operation(summary = "Prime the CSRF cookie")
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password")
    @Operation(summary = "Change the current user's password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AppUserDetails principal,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.getUserId(), request);
        return ResponseEntity.noContent().build();
    }
}
