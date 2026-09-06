package com.spendscope.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Limits for the anonymous demo endpoint, bound from {@code spendscope.demo.*}.
 *
 * <p>The endpoint has to be anonymous to be useful, which makes it the one
 * place an unauthenticated caller can create rows. Both a per-IP rate limit and
 * a retention window exist so that stays bounded.
 */
@ConfigurationProperties(prefix = "spendscope.demo")
public class DemoProperties {

    /** Demo accounts older than this can be deleted by the cleanup job. */
    private Duration retention = Duration.ofHours(24);

    /** Demo sessions one IP may create per {@link #rateLimitWindow}. */
    private int maxSessionsPerIp = 5;

    private Duration rateLimitWindow = Duration.ofHours(1);

    private boolean enabled = true;

    public Duration getRetention() {
        return retention;
    }

    public void setRetention(Duration retention) {
        this.retention = retention;
    }

    public int getMaxSessionsPerIp() {
        return maxSessionsPerIp;
    }

    public void setMaxSessionsPerIp(int maxSessionsPerIp) {
        this.maxSessionsPerIp = maxSessionsPerIp;
    }

    public Duration getRateLimitWindow() {
        return rateLimitWindow;
    }

    public void setRateLimitWindow(Duration rateLimitWindow) {
        this.rateLimitWindow = rateLimitWindow;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
