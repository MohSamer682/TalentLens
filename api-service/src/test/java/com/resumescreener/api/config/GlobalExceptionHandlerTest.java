package com.resumescreener.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void nullMessageRuntimeExceptionStillProducesBody() {
        // NPEs usually carry a null message; handler must not itself throw
        ResponseEntity<Map<String, String>> response =
                handler.handleRuntimeException(new NullPointerException());

        assertThat(response.getBody()).containsKey("message");
        assertThat(response.getBody().get("message")).isNotNull();
    }

    @Test
    void accessDeniedMapsTo403() {
        ResponseEntity<Map<String, String>> response =
                handler.handleAccessDenied(new AccessDeniedException("Not authorized or session not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("message")).contains("Not authorized");
    }

    @Test
    void dataIntegrityViolationDoesNotLeakSql() {
        ResponseEntity<Map<String, String>> response = handler.handleDataIntegrity(
                new org.springframework.dao.DataIntegrityViolationException(
                        "could not execute statement [ERROR: null value in column \"title\"]"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message")).doesNotContain("execute statement");
    }
}
