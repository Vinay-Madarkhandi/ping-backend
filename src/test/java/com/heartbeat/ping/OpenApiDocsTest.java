package com.heartbeat.ping;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the OpenAPI contract is actually published and covers the endpoints the frontend depends on.
 *
 * <p>This is the regression guard for the defect class that motivated adding springdoc: the frontend
 * lives in a separate repository, so a missing endpoint previously surfaced only as a silently dead UI
 * feature. If any of these paths drops out of the spec, this test fails first.
 *
 * <p>Requests go through the real security filter chain, so this also verifies the documentation
 * endpoints are reachable without a JWT cookie (they are permitAll, like the actuator scrape endpoint).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OpenApiDocsTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    private String fetchSpec() throws Exception {
        return mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void publishesApiDocsWithoutAuthentication() throws Exception {
        String spec = fetchSpec();

        assertThat(spec).contains("\"openapi\"");
        assertThat(spec).contains("Ping API");
    }

    @Test
    void specDocumentsTheEndpointsTheFrontendConsumes() throws Exception {
        String spec = fetchSpec();

        assertThat(spec)
                .contains("\"/api/v1/monitors\"")
                .contains("\"/api/v1/monitors/{monitorId}\"")
                .contains("\"/api/v1/monitors/{monitorId}/status\"")
                .contains("\"/api/v1/monitors/{monitorId}/logs\"")
                .contains("\"/api/v1/monitors/{monitorId}/incidents\"")
                .contains("\"/api/v1/monitors/{monitorId}/uptime\"")
                .contains("\"/api/v1/usage\"")
                .contains("\"/api/v1/plans\"")
                .contains("\"/api/v1/auth/me\"");
    }

    @Test
    void declaresTheCookieSecurityScheme() throws Exception {
        String spec = fetchSpec();

        // Generated clients need to know auth is a cookie, not a bearer header.
        assertThat(spec).contains("JwtTokenCookie");
        assertThat(spec).contains("\"in\":\"cookie\"");
        assertThat(spec).contains("\"name\":\"JwtToken\"");
    }
}
