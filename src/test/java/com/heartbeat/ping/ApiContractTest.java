package com.heartbeat.ping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.servlet.http.Cookie;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end HTTP contract test for the endpoints the Next.js frontend consumes.
 *
 * <p><b>Why this exists:</b> unit tests verify service logic, but the frontend depends on the exact
 * JSON <em>field names</em> emitted over the wire. Previously the UI called an endpoint that did not
 * exist and read fields the server never serialized, and nothing failed — the features just silently
 * did nothing. These assertions pin the wire contract, so any rename or dropped field breaks a build
 * instead of a dashboard.
 *
 * <p>Runs against real PostgreSQL (the plan catalog is Flyway-seeded reference data) and through the
 * real security filter chain using a genuine signin cookie.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ApiContractTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    private Cookie session;

    @BeforeEach
    void signUpAndSignIn() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String credentials = """
                {"username":"contract","email":"%s","password":"change-me"}
                """.formatted(email);

        mockMvc.perform(post("/api/v1/auth/signup/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials))
                .andExpect(status().isCreated());

        MockHttpServletResponse signin = mockMvc.perform(post("/api/v1/auth/signin/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"change-me"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();

        session = signin.getCookie("JwtToken");
        assertThat(session).as("signin must issue the JwtToken cookie").isNotNull();
    }

    /** Creates a monitor within the FREE plan limits (>= 300s interval, <= 5s timeout). */
    private String createMonitor() throws Exception {
        String body = mockMvc.perform(post("/api/v1/monitors")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"contract","url":"https://example.com",
                                 "intervalMilliseconds":300000,"timeoutMilliseconds":5000,
                                 "monitorMethod":"GET"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return body.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    @Test
    void usageEndpointReturnsTheFieldsTheDashboardReads() throws Exception {
        mockMvc.perform(get("/api/v1/usage").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monitorCount").exists())
                .andExpect(jsonPath("$.checksThisMonth").exists())
                .andExpect(jsonPath("$.alertsToday").exists())
                .andExpect(jsonPath("$.overQuota").isBoolean());
    }

    @Test
    void usageEndpointRejectsAnonymousCallers() throws Exception {
        mockMvc.perform(get("/api/v1/usage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void planCatalogExposesEveryLimitCheapestFirst() throws Exception {
        mockMvc.perform(get("/api/v1/plans").cookie(session))
                .andExpect(status().isOk())
                // Cheapest first: FREE (price 0) before PRO.
                .andExpect(jsonPath("$[0].name").value("FREE"))
                .andExpect(jsonPath("$[1].name").value("PRO"))
                .andExpect(jsonPath("$[0].maxMonitors").value(5))
                .andExpect(jsonPath("$[0].minIntervalMs").value(300000))
                .andExpect(jsonPath("$[0].maxTimeoutMs").value(5000))
                .andExpect(jsonPath("$[0].monthlyCheckQuota").value(50000))
                .andExpect(jsonPath("$[0].retentionDays").value(7))
                .andExpect(jsonPath("$[0].alertCooldownSeconds").value(3600))
                .andExpect(jsonPath("$[0].maxAlertsPerDay").value(50))
                .andExpect(jsonPath("$[0].priceAmount").value(0))
                .andExpect(jsonPath("$[0].currency").value("INR"))
                .andExpect(jsonPath("$[1].priceAmount").value(49900))
                .andExpect(jsonPath("$[1].durationDays").value(30));
    }

    @Test
    void monitorListCarriesStateSoTheUiNeedsNoPerMonitorStatusCall() throws Exception {
        createMonitor();

        mockMvc.perform(get("/api/v1/monitors").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].active").isBoolean())
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[0].paused").value(false))
                .andExpect(jsonPath("$[0].quotaBlocked").value(false))
                .andExpect(jsonPath("$[0].currentState").value("UNKNOWN"))
                .andExpect(jsonPath("$[0].displayState").value("UNKNOWN"))
                .andExpect(jsonPath("$[0].intervalMilliseconds").value(300000))
                .andExpect(jsonPath("$[0].timeoutMilliseconds").value(5000));
    }

    @Test
    void singleMonitorEndpointReturnsTheSameShapeAsTheList() throws Exception {
        String monitorId = createMonitor();

        mockMvc.perform(get("/api/v1/monitors/" + monitorId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(monitorId))
                .andExpect(jsonPath("$.displayState").value("UNKNOWN"))
                .andExpect(jsonPath("$.quotaBlocked").value(false))
                .andExpect(jsonPath("$.intervalMilliseconds").value(300000));
    }

    @Test
    void singleMonitorEndpointHidesMonitorsTheCallerDoesNotOwn() throws Exception {
        // A random id must be indistinguishable from someone else's monitor: 403, never 404.
        mockMvc.perform(get("/api/v1/monitors/" + UUID.randomUUID()).cookie(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void monitorStatusExposesQuotaBlockedForTheQuotaUi() throws Exception {
        String monitorId = createMonitor();

        mockMvc.perform(get("/api/v1/monitors/" + monitorId + "/status").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quotaBlocked").value(false))
                .andExpect(jsonPath("$.displayState").value("UNKNOWN"))
                .andExpect(jsonPath("$.currentState").value("UNKNOWN"));
    }

    @Test
    void currentUserExposesThePlanUsingTheSharedPlanShape() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.subscriptionStatus").value("FREE"))
                // Same field names as GET /api/v1/plans entries, so the client models one plan type.
                .andExpect(jsonPath("$.plan.name").value("FREE"))
                .andExpect(jsonPath("$.plan.maxMonitors").value(5))
                .andExpect(jsonPath("$.plan.priceAmount").value(0))
                .andExpect(jsonPath("$.plan.currency").value("INR"));
    }
}
