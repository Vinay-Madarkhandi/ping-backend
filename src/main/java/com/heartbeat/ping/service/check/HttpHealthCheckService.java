package com.heartbeat.ping.service.check;

import com.heartbeat.ping.config.properties.HttpClientProperties;
import com.heartbeat.ping.modles.MonitorMethod;
import com.heartbeat.ping.service.security.SsrfBlockedHostException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Service;

/**
 * Apache HttpClient 5 implementation of {@link HealthCheckService}. Uses the shared pooled
 * client, applying each monitor's timeout, redirect policy and custom headers per request,
 * and evaluating success against the expected status code and optional keyword.
 *
 * <p>Performs no database access — it is intentionally called outside any transaction so the
 * network round-trip never pins a JDBC connection.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HttpHealthCheckService implements HealthCheckService {

    private final CloseableHttpClient httpClient;
    private final HttpClientProperties httpProps;

    @Override
    public CheckResult check(CheckSpec spec) {
        long timeoutMs = spec.timeoutMillis() > 0
                ? spec.timeoutMillis()
                : httpProps.getResponseTimeout().toMillis();
        boolean needBody = spec.keyword() != null && !spec.keyword().isBlank();

        ClassicHttpRequest request = buildRequest(spec);
        HttpClientContext context = contextFor(spec, timeoutMs);

        long start = System.currentTimeMillis();
        try {
            return httpClient.execute(request, context, response -> {
                long elapsed = System.currentTimeMillis() - start;
                int code = response.getCode();
                String body = readBody(response.getEntity(), needBody);
                return evaluate(spec, code, body, elapsed);
            });
        } catch (Exception ex) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("Monitor {} check failed: {}", spec.monitorId(), ex.getMessage());
            return classify(ex, elapsed);
        }
    }

    /**
     * Distinguishes our-infrastructure failures (pool exhausted, SSRF-blocked resolution, interrupted)
     * from target-attributable failures. The former are INCONCLUSIVE and never open an incident.
     */
    private CheckResult classify(Exception ex, long elapsed) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ConnectionRequestTimeoutException
                    || t instanceof SsrfBlockedHostException
                    || t instanceof InterruptedException) {
                return CheckResult.inconclusive(elapsed, ex.getMessage());
            }
        }
        return CheckResult.down(0, elapsed, ex.getMessage());
    }

    private ClassicHttpRequest buildRequest(CheckSpec spec) {
        ClassicHttpRequest request = spec.method() == MonitorMethod.POST
                ? new HttpPost(spec.url())
                : new HttpGet(spec.url());
        spec.customHeaders().forEach(request::addHeader);
        return request;
    }

    private HttpClientContext contextFor(CheckSpec spec, long timeoutMs) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(Timeout.ofMilliseconds(timeoutMs))
                .setRedirectsEnabled(spec.followRedirects())
                .build();
        HttpClientContext context = HttpClientContext.create();
        context.setRequestConfig(requestConfig);
        return context;
    }

    private String readBody(HttpEntity entity, boolean needBody) {
        if (entity == null) {
            return null;
        }
        try {
            if (needBody) {
                return EntityUtils.toString(entity);
            }
            EntityUtils.consume(entity); // release the connection back to the pool
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private CheckResult evaluate(CheckSpec spec, int code, String body, long elapsed) {
        if (!statusMatches(code, spec.expectedStatusCode())) {
            return CheckResult.down(code, elapsed, "Unexpected status code: " + code);
        }
        String keyword = spec.keyword();
        if (keyword != null && !keyword.isBlank() && (body == null || !body.contains(keyword))) {
            return CheckResult.down(code, elapsed, "Keyword not found: " + keyword);
        }
        return CheckResult.up(code, elapsed);
    }

    private boolean statusMatches(int code, Integer expected) {
        return expected != null ? code == expected : (code >= 200 && code < 400);
    }
}
