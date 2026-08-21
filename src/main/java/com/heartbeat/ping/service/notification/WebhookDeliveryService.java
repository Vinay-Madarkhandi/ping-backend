package com.heartbeat.ping.service.notification;

import com.heartbeat.ping.config.properties.WebhookProperties;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * POSTs a webhook payload using the same SSRF-protected, connection-pooled client as monitor
 * health checks ({@code healthCheckHttpClient} — its connection manager validates the DNS-resolved
 * IP, closing the rebinding window an unguarded client would have).
 */
@Service
public class WebhookDeliveryService {

    private final CloseableHttpClient httpClient;
    private final WebhookProperties webhookProps;

    public WebhookDeliveryService(CloseableHttpClient healthCheckHttpClient, WebhookProperties webhookProps) {
        this.httpClient = healthCheckHttpClient;
        this.webhookProps = webhookProps;
    }

    /** Throws on any non-2xx response or transport failure — the caller decides how to record it. */
    public void deliver(String targetUrl, String jsonPayload) throws IOException {
        HttpPost request = new HttpPost(targetUrl);
        request.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));

        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(Timeout.ofMilliseconds(webhookProps.getRequestTimeout().toMillis()))
                .build();
        HttpClientContext context = HttpClientContext.create();
        context.setRequestConfig(requestConfig);

        httpClient.execute(request, context, response -> {
            int code = response.getCode();
            EntityUtils.consume(response.getEntity());
            if (code < 200 || code >= 300) {
                throw new IOException("Webhook endpoint returned status " + code);
            }
            return null;
        });
    }
}
