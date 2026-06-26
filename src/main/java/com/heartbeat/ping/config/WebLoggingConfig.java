package com.heartbeat.ping.config;

import com.heartbeat.ping.filters.CorrelationIdFilter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Wiring for cross-cutting observability concerns: the request correlation filter and a common
 * {@code application} tag stamped on every metric exported to Prometheus.
 */
@Configuration
public class WebLoggingConfig {

    /**
     * Registers {@link CorrelationIdFilter} at the highest precedence so the correlation id (and the
     * per-request log line) wraps the entire chain — including the security filters — and is present
     * even for requests that are rejected before reaching a controller.
     */
    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    /**
     * Tags every metric with {@code application} so dashboards/alerts can scope queries by service
     * (and remain correct if this Prometheus is later shared with other apps).
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonMetricsTags(
            @Value("${spring.application.name:ping}") String applicationName) {
        return registry -> registry.config().commonTags("application", applicationName);
    }
}
