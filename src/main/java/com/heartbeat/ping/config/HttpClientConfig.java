package com.heartbeat.ping.config;

import com.heartbeat.ping.config.properties.HttpClientProperties;
import com.heartbeat.ping.service.security.SsrfValidatingDnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds a single, shared, connection-pooled Apache HttpClient 5 used for all
 * outbound health checks. Per-monitor timeout and redirect policy are applied
 * per request via {@code HttpClientContext}, so one pooled client serves every monitor.
 *
 * <p>The connection manager uses {@link SsrfValidatingDnsResolver}, so the IP the client
 * connects to is the IP that was SSRF-validated (closes the DNS-rebinding window).
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public PoolingHttpClientConnectionManager healthCheckConnectionManager(
            HttpClientProperties props, SsrfValidatingDnsResolver dnsResolver) {
        return PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(props.getMaxTotal())
                .setMaxConnPerRoute(props.getMaxPerRoute())
                .setDnsResolver(dnsResolver)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(props.getConnectTimeout().toMillis()))
                        .setSocketTimeout(Timeout.ofMilliseconds(props.getResponseTimeout().toMillis()))
                        .build())
                .build();
    }

    @Bean(destroyMethod = "close")
    public CloseableHttpClient healthCheckHttpClient(PoolingHttpClientConnectionManager connectionManager,
                                                     HttpClientProperties props) {
        RequestConfig defaultRequestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(props.getConnectTimeout().toMillis()))
                .setResponseTimeout(Timeout.ofMilliseconds(props.getResponseTimeout().toMillis()))
                .setRedirectsEnabled(true)
                .build();

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(defaultRequestConfig)
                .evictExpiredConnections()
                .disableAutomaticRetries() // a monitor failure must surface, not be silently retried
                .build();
    }
}
