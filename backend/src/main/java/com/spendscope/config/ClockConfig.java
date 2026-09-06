package com.spendscope.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Exposes the system clock as a bean.
 *
 * <p>Calling {@code LocalDate.now()} inside a service makes "what does this
 * period resolve to?" untestable without waiting for the calendar. Injecting a
 * {@link Clock} lets tests pin today to a fixed date and assert exact
 * boundaries.
 *
 * <p>UTC by default. User-facing dates are {@code LocalDate} values that carry
 * no zone; only the instants in {@code created_at}/{@code updated_at} are
 * absolute, and those belong in UTC.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
