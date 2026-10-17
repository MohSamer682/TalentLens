package com.resumescreener.api.analytics;

import com.resumescreener.api.analytics.dto.AnalyticsOverview;
import com.resumescreener.api.audit.AuditService;
import com.resumescreener.api.common.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Runs the real @PreAuthorize rule; GlobalExceptionHandlerTest covers AccessDenied -> 403 envelope. */
@SpringJUnitConfig(AnalyticsControllerTest.Config.class)
class AnalyticsControllerTest {

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean AnalyticsRepository repository() { return mock(AnalyticsRepository.class); }
        @Bean AnalyticsService service(AnalyticsRepository r) { return new AnalyticsService(r); }
        @Bean AnalyticsController controller(AnalyticsService s) { return new AnalyticsController(s, mock(AuditService.class)); }
    }

    @Autowired AnalyticsController controller;
    @Autowired AnalyticsService service;
    @Autowired AnalyticsRepository repository;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        reset(repository);
    }

    @Test
    void adminGetsOverview() {
        signIn("ROLE_ADMIN");
        when(repository.overview(any(), any())).thenReturn(empty());

        assertThat(controller.overview("90").getBody().getData()).isNotNull();
    }

    @Test
    void hrChiefGetsOverview() {
        signIn("ROLE_HR_CHIEF");
        when(repository.overview(any(), any())).thenReturn(empty());

        assertThat(controller.overview("30").getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void recruiterIsForbidden() {
        signIn("ROLE_RECRUITER");

        assertThrows(AccessDeniedException.class, () -> controller.overview("90"));
    }

    @Test
    void invalidDaysIsRejected() {
        signIn("ROLE_ADMIN");

        for (String days : new String[]{"7", "abc", ""}) {
            ApiException e = assertThrows(ApiException.class, () -> controller.overview(days));
            assertEquals("Choose 30, 90 or 365 days", e.getMessage());
        }
    }

    @Test
    void periodEndsAtEndOfTodayUtc() {
        service.setClock(Clock.fixed(Instant.parse("2026-10-17T15:30:00Z"), ZoneOffset.UTC));

        service.overview("90");

        verify(repository).overview(Instant.parse("2026-07-20T00:00:00Z"), Instant.parse("2026-10-18T00:00:00Z"));
        service.setClock(Clock.systemUTC());
    }

    private static void signIn(String authority) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u@example.com", null, authority));
    }

    private static AnalyticsOverview empty() {
        return new AnalyticsOverview(null, null, null, java.util.List.of(), java.util.List.of(), java.util.List.of());
    }
}
