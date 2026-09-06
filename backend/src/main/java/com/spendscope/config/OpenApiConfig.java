package com.spendscope.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata.
 *
 * <p>Documents the session cookie as the security scheme so the generated docs
 * describe how the API is actually called. Disabled entirely in production via
 * {@code springdoc.api-docs.enabled=false}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI spendScopeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SpendScope API")
                        .version("1.0.0")
                        .description("""
                                Personal finance analytics API.

                                Authentication is session based. Call POST /api/auth/login, then send the
                                JSESSIONID cookie with every request. State-changing requests must also echo
                                the XSRF-TOKEN cookie in the X-XSRF-TOKEN header.

                                Every resource is scoped to the authenticated user: ids from another account
                                return 404, never another user's data.
                                """)
                        .license(new License().name("MIT")))
                .components(new Components()
                        .addSecuritySchemes("session", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")));
    }
}
