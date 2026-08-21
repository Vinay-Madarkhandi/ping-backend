package com.heartbeat.ping.service.execution;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Builds the public URL an external job pings to report a HEARTBEAT monitor as alive. */
@Component
public class HeartbeatUrlBuilder {

    @Value("${api.base-url}")
    private String apiBaseUrl;

    public String urlFor(String heartbeatToken) {
        return apiBaseUrl + "/api/v1/public/heartbeat/" + heartbeatToken;
    }
}
