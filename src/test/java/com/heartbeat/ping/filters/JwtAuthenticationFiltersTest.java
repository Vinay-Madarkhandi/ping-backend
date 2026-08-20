package com.heartbeat.ping.filters;

import com.heartbeat.ping.helpers.AuthUserDetails;
import com.heartbeat.ping.modles.User;
import com.heartbeat.ping.service.JwtService;
import com.heartbeat.ping.service.UserDetailServiceImpl;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Docker-free unit test for the security-critical rejection paths added on top of the base
 * expiry/revocation check: a soft-deleted account, and a token issued before the current account's
 * row was created (email freed by a delete and re-registered by someone else).
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFiltersTest {

    private static final String TOKEN = "fake-token";
    private static final String EMAIL = "user@example.com";

    @Mock private JwtService jwtService;
    @Mock private UserDetailServiceImpl userDetailService;
    @Mock private FilterChain filterChain;

    private JwtAuthenticationFilters filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilters();
        ReflectionTestUtils.setField(filter, "jwtService", jwtService);
        ReflectionTestUtils.setField(filter, "userDetailService", userDetailService);
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWithCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/monitors");
        request.setServletPath("/api/v1/monitors");
        request.setCookies(new jakarta.servlet.http.Cookie("JwtToken", TOKEN));
        return request;
    }

    private AuthUserDetails authUserDetails(LocalDateTime createdAt, Instant deletedAt) {
        User user = User.builder()
                .userName("jane").email(EMAIL).passwordHash("hash")
                .build();
        user.setId(UUID.randomUUID());
        user.setCreatedAt(createdAt);
        user.setDeletedAt(deletedAt);
        return new AuthUserDetails(user);
    }

    @Test
    void authenticatesWhenTokenIsValidAccountEnabledAndNotStale() throws Exception {
        AuthUserDetails userDetails = authUserDetails(LocalDateTime.now().minusDays(1), null);
        when(jwtService.extractEmail(TOKEN)).thenReturn(EMAIL);
        when(userDetailService.loadUserByUsername(EMAIL)).thenReturn(userDetails);
        when(jwtService.validateToken(TOKEN, EMAIL)).thenReturn(true);
        when(jwtService.extractIssuedAt(TOKEN)).thenReturn(new Date());

        filter.doFilterInternal(requestWithCookie(), new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void rejectsTokenForSoftDeletedAccount() throws Exception {
        AuthUserDetails userDetails = authUserDetails(LocalDateTime.now().minusDays(30), Instant.now());
        when(jwtService.extractEmail(TOKEN)).thenReturn(EMAIL);
        when(userDetailService.loadUserByUsername(EMAIL)).thenReturn(userDetails);
        when(jwtService.validateToken(TOKEN, EMAIL)).thenReturn(true);

        filter.doFilterInternal(requestWithCookie(), new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void rejectsTokenIssuedBeforeTheCurrentAccountWasCreated() throws Exception {
        // Simulates: old account deleted, email freed, a different person re-registers with the
        // same email. The new row's createdAt is after the stale token's iat.
        LocalDateTime accountCreatedAt = LocalDateTime.now();
        AuthUserDetails userDetails = authUserDetails(accountCreatedAt, null);
        Date staleIssuedAt = Date.from(accountCreatedAt.minusDays(1).atZone(java.time.ZoneId.systemDefault()).toInstant());

        when(jwtService.extractEmail(TOKEN)).thenReturn(EMAIL);
        when(userDetailService.loadUserByUsername(EMAIL)).thenReturn(userDetails);
        when(jwtService.validateToken(TOKEN, EMAIL)).thenReturn(true);
        when(jwtService.extractIssuedAt(TOKEN)).thenReturn(staleIssuedAt);

        filter.doFilterInternal(requestWithCookie(), new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(any(), any());
    }
}
