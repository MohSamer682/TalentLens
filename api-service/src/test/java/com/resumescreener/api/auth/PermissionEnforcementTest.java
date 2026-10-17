package com.resumescreener.api.auth;

import com.resumescreener.api.admin.RoleController;
import com.resumescreener.api.admin.RoleService;
import com.resumescreener.api.analytics.AnalyticsController;
import com.resumescreener.api.analytics.AnalyticsService;
import com.resumescreener.api.audit.AuditService;
import com.resumescreener.api.candidate.CandidateController;
import com.resumescreener.api.candidate.CandidateService;
import com.resumescreener.api.candidate.dto.ReviewRequest;
import com.resumescreener.api.llm.LlmStatusClient;
import com.resumescreener.api.session.SessionController;
import com.resumescreener.api.session.SessionService;
import com.resumescreener.api.session.dto.CreateSessionRequest;
import com.resumescreener.api.upload.MinioUploadService;
import com.resumescreener.api.upload.UploadController;
import com.resumescreener.common.session.CandidateRepository;
import com.resumescreener.common.session.ReviewStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The real @PreAuthorize rules on each guarded controller, driven by permission authorities rather than roles. */
@SpringJUnitConfig(PermissionEnforcementTest.Config.class)
class PermissionEnforcementTest {

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean SessionService sessionService() { return mock(SessionService.class); }
        @Bean SessionController sessionController(SessionService s) {
            return new SessionController(s, mock(CandidateRepository.class), mock(LlmStatusClient.class));
        }
        @Bean UploadController uploadController() { return new UploadController(mock(MinioUploadService.class), mock(AuditService.class)); }
        @Bean CandidateController candidateController() { return new CandidateController(mock(CandidateService.class)); }
        @Bean RoleController roleController() { return new RoleController(mock(RoleService.class)); }
        @Bean AnalyticsController analyticsController() { return new AnalyticsController(mock(AnalyticsService.class), mock(AuditService.class)); }
    }

    @Autowired SessionController sessions;
    @Autowired SessionService sessionService;
    @Autowired UploadController uploads;
    @Autowired CandidateController candidates;
    @Autowired AnalyticsController analytics;
    @Autowired RoleController roles;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sessionWritesRequireSessionRun() {
        Authentication auth = signIn("ROLE_RECRUITER", "CANDIDATE_REVIEW");
        when(sessionService.getSessions(anyString(), anyBoolean(), any())).thenReturn(Page.empty());

        assertThrows(AccessDeniedException.class, () -> sessions.createSession(new CreateSessionRequest(), auth));
        assertThrows(AccessDeniedException.class, () -> sessions.deleteSession("s1", auth));
        assertThrows(AccessDeniedException.class, () -> uploads.removeResume("s1", "c1", auth));
        assertDoesNotThrow(() -> sessions.listSessions(PageRequest.of(0, 20), false, auth));
    }

    @Test
    void sessionRunAllowsWrites() {
        Authentication auth = signIn("ROLE_RECRUITER", "SESSION_RUN");

        assertThat(uploads.removeResume("s1", "c1", auth).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void reviewRequiresCandidateReview() {
        Authentication auth = signIn("ROLE_RECRUITER", "SESSION_RUN");

        assertThrows(AccessDeniedException.class,
                () -> candidates.review("s1", "c1", new ReviewRequest(ReviewStatus.SHORTLISTED), auth));
    }

    @Test
    void analyticsRequiresAnalyticsView() {
        signIn("ROLE_HR_CHIEF", "SESSION_RUN", "SESSION_VIEW_ALL");

        assertThrows(AccessDeniedException.class, () -> analytics.overview("90"));
    }

    @Test
    void hrChiefCannotManageRolesEvenWithEveryOtherPermission() {
        signIn("ROLE_HR_CHIEF", "SESSION_RUN", "SESSION_VIEW_ALL", "CANDIDATE_REVIEW", "ANALYTICS_VIEW");

        assertThrows(AccessDeniedException.class, () -> roles.list());
    }

    @Test
    void hrChiefCannotCreateSessions() {
        Authentication auth = signIn("ROLE_HR_CHIEF", "SESSION_VIEW_ALL", "CANDIDATE_REVIEW", "ANALYTICS_VIEW");
        when(sessionService.getSessions(anyString(), anyBoolean(), any())).thenReturn(Page.empty());

        assertThrows(AccessDeniedException.class, () -> sessions.createSession(new CreateSessionRequest(), auth));
        assertDoesNotThrow(() -> sessions.listSessions(PageRequest.of(0, 20), true, auth));
    }

    private static Authentication signIn(String... authorities) {
        Authentication auth = new TestingAuthenticationToken("u@example.com", null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
        return auth;
    }
}
