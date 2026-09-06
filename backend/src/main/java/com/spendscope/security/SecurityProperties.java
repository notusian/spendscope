package com.spendscope.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Security knobs that differ between environments.
 *
 * <p>Bound from {@code spendscope.security.*}. Every value has a safe default
 * for local development and is overridden by environment variables in
 * production - see {@code .env.example}.
 */
@ConfigurationProperties(prefix = "spendscope.security")
@Validated
public class SecurityProperties {

    /**
     * Exact origins allowed to call the API with credentials.
     *
     * <p>Never a wildcard: {@code Access-Control-Allow-Origin: *} is illegal
     * together with {@code Allow-Credentials: true}, and allowing any origin
     * would let a hostile page drive the API using the victim's session cookie.
     */
    @NotEmpty
    private List<String> allowedOrigins = List.of("http://localhost:5173");

    /** Maximum failed logins per email+IP pair inside {@link #loginAttemptWindow}. */
    @Min(1)
    private int maxLoginAttempts = 10;

    private Duration loginAttemptWindow = Duration.ofMinutes(15);

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public int getMaxLoginAttempts() {
        return maxLoginAttempts;
    }

    public void setMaxLoginAttempts(int maxLoginAttempts) {
        this.maxLoginAttempts = maxLoginAttempts;
    }

    public Duration getLoginAttemptWindow() {
        return loginAttemptWindow;
    }

    public void setLoginAttemptWindow(Duration loginAttemptWindow) {
        this.loginAttemptWindow = loginAttemptWindow;
    }
}
