package com.heartbeat.ping.config;

import com.heartbeat.ping.config.properties.AdminProperties;
import com.heartbeat.ping.filters.JwtAuthenticationEntryPoint;
import com.heartbeat.ping.filters.JwtAuthenticationFilters;
import com.heartbeat.ping.service.UserDetailServiceImpl;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

@Configuration
@EnableWebSecurity
public class SpringSecurity {

    @Autowired
    private JwtAuthenticationFilters jwtAuthenticationFilters;

    @Autowired
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Autowired
    private AdminProperties adminProperties;

    @Bean
    public UserDetailsService userDetailsService(){
        return new UserDetailServiceImpl();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex ->
                        ex.authenticationEntryPoint(jwtAuthenticationEntryPoint)
                )
                .authorizeHttpRequests(auth ->
                        auth
                                .requestMatchers("/api/v1/auth/signup/**").permitAll()
                                .requestMatchers("/api/v1/auth/signin/**").permitAll()
                                .requestMatchers("/api/v1/health").permitAll()
                                .requestMatchers("/error").permitAll()
                                // Razorpay calls this server-to-server; it is authenticated by HMAC, not JWT.
                                .requestMatchers("/api/v1/webhooks/**").permitAll()
                                // Published status pages are meant to be viewed by anyone with the link —
                                // that's the entire point of the feature. StatusPageService's public
                                // projection is what keeps this from leaking anything beyond the owner's
                                // chosen monitor names, states, and uptime percentages.
                                .requestMatchers("/api/v1/public/**").permitAll()
                                // /actuator/health/** also covers the liveness/readiness probe groups.
                                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                                // loggers/threaddump/metrics expose operational internals (can change log
                                // levels at runtime, dump thread state, read request-level metrics) — that's
                                // admin territory, not "any logged-in user". Same email allowlist as
                                // /api/v1/admin/**, since there is no role system.
                                .requestMatchers("/actuator/loggers", "/actuator/loggers/**",
                                        "/actuator/threaddump", "/actuator/metrics", "/actuator/metrics/**")
                                        .access(adminOnly())
                                // OpenAPI spec + Swagger UI. Like actuator, these are unauthenticated so
                                // tooling can fetch the contract — network-restrict them in production.
                                .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                                .requestMatchers("/api/v1/auth/validate").authenticated()
                                .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilters, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider(){
        DaoAuthenticationProvider daoAuthenticationProvider = new DaoAuthenticationProvider(userDetailsService());
        daoAuthenticationProvider.setPasswordEncoder(passwordEncoder());

        return daoAuthenticationProvider;

    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration){
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    /** Grants access only to authenticated principals on the {@code monitor.admin.emails} allowlist. */
    private AuthorizationManager<RequestAuthorizationContext> adminOnly() {
        return (authentication, context) -> {
            Authentication auth = authentication.get();
            boolean granted = auth != null && auth.isAuthenticated()
                    && adminProperties.getEmails().contains(auth.getName());
            return new AuthorizationDecision(granted);
        };
    }

}
