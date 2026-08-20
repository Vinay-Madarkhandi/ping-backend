package com.heartbeat.ping.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publishes the API as an OpenAPI 3 document at {@code /v3/api-docs} (Swagger UI at
 * {@code /swagger-ui.html}).
 *
 * <p><b>Why this exists:</b> the frontend lives in a separate repository with no shared build, so
 * nothing mechanically verified that the two agreed. That drift produced real defects — a UI calling
 * an endpoint that never existed, and fields the server never serialized. A generated schema turns
 * those into build-time errors for any typed client instead of silently dead features.
 *
 * <p>The spec is derived from Spring MVC metadata, so controllers need no annotations; adding them
 * is optional polish, not a requirement for the contract to be complete.
 */
@Configuration
public class OpenApiConfig {

    /** Matches the cookie name issued by {@code AuthController#signIn}. */
    private static final String COOKIE_SCHEME = "JwtTokenCookie";
    private static final String COOKIE_NAME = "JwtToken";

    @Bean
    public OpenAPI pingOpenApi(@Value("${spring.application.name:ping}") String applicationName) {
        return new OpenAPI()
                .info(new Info()
                        .title("Ping API")
                        .version("0.0.1-SNAPSHOT")
                        .description("""
                                Uptime monitoring API: HTTP monitors, duration-based uptime, incidents,
                                alerting, per-plan usage limits and Razorpay billing.

                                Authentication is a stateless JWT carried in the HttpOnly `JwtToken`
                                cookie returned by `POST /api/v1/auth/signin/user`. Actuator and these
                                documentation endpoints are unauthenticated and must be network-restricted
                                in production.
                                """)
                        .license(new License().name("Proprietary")))
                // Declared so generated clients know requests are cookie-authenticated, not bearer.
                .components(new Components().addSecuritySchemes(COOKIE_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name(COOKIE_NAME)
                        .description("JWT session cookie set by signin (HttpOnly).")))
                // Global default: every endpoint requires the cookie except the few marked permitAll
                // in SpringSecurity (signup, signin, health, webhooks, actuator, these docs).
                .addSecurityItem(new SecurityRequirement().addList(COOKIE_SCHEME));
    }
}
