package com.heartbeat.ping.controller;

import com.heartbeat.ping.dto.Error.ErrorResponse;
import com.heartbeat.ping.service.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/monitors/123");
        return request;
    }

    @Test
    void resourceNotFoundMapsTo404WithItsOwnMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleNotFound(new ResourceNotFoundException("User not found"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("User not found");
    }

    @Test
    void unexpectedExceptionMapsTo500WithoutLeakingItsMessage() {
        Exception dbFailure = new IllegalStateException("connection refused to jdbc:postgresql://internal-host/db");

        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(dbFailure, request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage())
                .isEqualTo("An unexpected error occurred")
                .doesNotContain("jdbc:postgresql", "internal-host");
    }
}
