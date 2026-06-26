package com.heartbeat.ping.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Assigns a correlation id to every HTTP request and exposes it for logging.
 *
 * <p>The id is read from the inbound {@code X-Request-Id} header (so it can be propagated from an
 * upstream proxy/gateway) or generated when absent. It is placed in the SLF4J {@link MDC} under
 * {@code traceId} — picked up by the {@code [%X{traceId}]} slot in {@code logback-spring.xml} — and
 * echoed back on the response so a client/operator can grep logs by the id they were handed.
 *
 * <p>Registered ahead of the security chain (see {@code WebLoggingConfig}) so even rejected requests
 * are correlated. The MDC is always cleared in {@code finally} to prevent the id leaking onto the
 * next request handled by a pooled thread.
 */
@Slf4j
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_TRACE_ID = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String traceId = resolveTraceId(request);
        MDC.put(MDC_TRACE_ID, traceId);
        response.setHeader(REQUEST_ID_HEADER, traceId);

        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            logCompletion(request, response, elapsedMs);
            MDC.remove(MDC_TRACE_ID);
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        if (StringUtils.hasText(incoming)) {
            // Cap defensively — never let an attacker-controlled header bloat every log line.
            return incoming.length() > 64 ? incoming.substring(0, 64) : incoming;
        }
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private void logCompletion(HttpServletRequest request, HttpServletResponse response, long elapsedMs) {
        String uri = request.getRequestURI();
        // Actuator scrapes are high-frequency and uninteresting at INFO; keep them at DEBUG.
        if (uri.startsWith("/actuator")) {
            log.debug("{} {} -> {} ({} ms)", request.getMethod(), uri, response.getStatus(), elapsedMs);
        } else {
            log.info("{} {} -> {} ({} ms)", request.getMethod(), uri, response.getStatus(), elapsedMs);
        }
    }
}
