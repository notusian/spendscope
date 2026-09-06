package com.spendscope;

import com.spendscope.categorization.CategorizationProperties;
import com.spendscope.demo.DemoProperties;
import com.spendscope.importjob.CsvImportProperties;
import com.spendscope.security.SecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry point.
 *
 * <p>SpendScope is a modular monolith: one deployable Spring Boot application
 * split into domain packages ({@code account}, {@code transaction},
 * {@code analytics}, ...) that talk to each other through services, never
 * through each other's repositories.
 */
@SpringBootApplication
@EnableConfigurationProperties({
        SecurityProperties.class,
        CsvImportProperties.class,
        CategorizationProperties.class,
        DemoProperties.class
})
@EnableScheduling
public class SpendScopeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpendScopeApplication.class, args);
    }
}
