package com.heartbeat.ping.filters;

import com.heartbeat.ping.helpers.AuthUserDetails;
import com.heartbeat.ping.service.JwtService;
import com.heartbeat.ping.service.UserDetailServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.Date;

@Slf4j
@Component
public class JwtAuthenticationFilters extends OncePerRequestFilter {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserDetailServiceImpl userDetailService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getServletPath();

        // Skip auth endpoints — includes /webhooks, which is permitAll in SpringSecurity but was
        // missing here, so every legitimate Razorpay callback used to log a misleading "missing
        // cookie" WARN even though the request was never expected to carry one.
        if (path.startsWith("/api/v1/auth/signin")
                || path.startsWith("/api/v1/auth/signup")
                || path.startsWith("/api/v1/health")
                || path.startsWith("/api/v1/webhooks")
                || path.startsWith("/api/v1/public")
                || path.startsWith("/actuator/health")
                || path.startsWith("/actuator/info")
                || path.startsWith("/actuator/prometheus")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/error")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("JwtToken".equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("JWT auth check for {} {}: JwtToken cookie present={}",
                    request.getMethod(), path, token != null);
        }

        if (token != null) {
            try {
                String email = jwtService.extractEmail(token);

                if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = userDetailService.loadUserByUsername(email);

                    if (!jwtService.validateToken(token, userDetails.getUsername())) {
                        log.warn("JWT auth rejected for {} {}: token validation returned false for subject {}",
                                request.getMethod(), path, email);
                    } else if (!userDetails.isEnabled()) {
                        // Soft-deleted account: a token from before the deletion is still
                        // cryptographically valid and may not yet be individually revoked.
                        log.warn("JWT auth rejected for {} {}: account for subject {} is deleted",
                                request.getMethod(), path, email);
                    } else if (issuedBeforeAccountCreated(token, userDetails)) {
                        // The email was freed by a soft-delete and re-registered by someone else;
                        // without this check a pre-deletion token would authenticate as the new
                        // account that happens to share the same email.
                        log.warn("JWT auth rejected for {} {}: token predates the current account for subject {}",
                                request.getMethod(), path, email);
                    } else {
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                        userDetails,
                                        null,
                                        userDetails.getAuthorities()
                                );

                        authToken.setDetails(
                                new WebAuthenticationDetailsSource()
                                        .buildDetails(request)
                        );

                        SecurityContextHolder.getContext()
                                .setAuthentication(authToken);
                        log.debug("JWT auth success for {} {}", request.getMethod(), path);
                    }
                }
            } catch (Exception e) {
                log.error("JWT authentication failed for {} {}", request.getMethod(), path, e);
            }
        } else {
            log.warn("JWT auth missing JwtToken cookie for {} {}", request.getMethod(), path);
        }

        filterChain.doFilter(request, response);
    }

    private boolean issuedBeforeAccountCreated(String token, UserDetails userDetails) {
        if (!(userDetails instanceof AuthUserDetails authUserDetails)) {
            return false;
        }
        LocalDateTime createdAt = authUserDetails.getCreatedAt();
        Date issuedAt = jwtService.extractIssuedAt(token);
        if (createdAt == null || issuedAt == null) {
            return false;
        }
        Date createdAtAsDate = Date.from(createdAt.atZone(ZoneId.systemDefault()).toInstant());
        return issuedAt.before(createdAtAsDate);
    }
}
