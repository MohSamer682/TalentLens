package com.resumescreener.api.portal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumescreener.api.portal.dto.ApplicationView;
import com.resumescreener.api.portal.dto.PortalStage;
import com.resumescreener.api.portal.dto.StepState;
import com.resumescreener.api.support.ApiIntegrationTest;
import com.resumescreener.common.session.InterviewKind;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PortalApplicationsApiTest extends ApiIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired PortalQueryService portal;
    @Autowired DataSource dataSource;
    @Autowired ObjectMapper json;

    User recruiter;
    User interviewer;
    User nour;
    String sessionId;

    @BeforeEach
    void seed() {
        recruiter = user(Role.RECRUITER);
        interviewer = user(Role.TECHNICAL_INTERVIEWER);
        nour = user(Role.CANDIDATE);
        jdbc.update("INSERT INTO user_profiles (user_id, headline, updated_at) VALUES (?, 'Senior Backend Engineer', now())", interviewer.getId());
        sessionId = session("Senior Backend Engineer", "BE-204");
    }

    private String session(String jobTitle, String code) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO screening_sessions (id, user_id, title, job_title, position_code, job_description, status, created_at) "
                + "VALUES (?, ?, 'Backend 2026', ?, ?, 'Build the payments API', 'DONE', now())", id, recruiter.getId(), jobTitle, code);
        jdbc.update("INSERT INTO session_required_skills (session_id, position, skill) VALUES (?, 0, 'Java'), (?, 1, 'Spring Boot')", id, id);
        return id;
    }

    private String application(User account, String review, String plan) {
        return application(sessionId, account, review, plan);
    }

    private String application(String session, User account, String review, String plan) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO candidates (id, session_id, name, email, status, review_status, interview_plan, account_user_id, created_at) "
                + "VALUES (?, ?, 'Nour Hassan', ?, 'SCORED', ?, ?, ?, now())", id, session, account.getEmail(), review, plan, account.getId());
        jdbc.update("INSERT INTO candidate_scores (id, candidate_id, composite_score, justification) VALUES (?, ?, 82.4, 'internal')",
                UUID.randomUUID().toString(), id);
        return id;
    }

    private String interview(String candidateId, String kind, Instant start, String place, String location, String state) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO candidate_interviews (id, candidate_id, kind, interviewer_id, scheduled_start, duration_minutes, place_type, location, "
                        + "note_to_interviewer, status, rating, feedback_assessment, created_by) VALUES (?, ?, ?, ?, ?, 45, ?, ?, 'Probe the gaps', ?, 4, 'Solid', ?)",
                id, candidateId, kind, interviewer.getId(), Timestamp.from(start), place, location, state, recruiter.getId());
        return id;
    }

    private static Instant inDays(int days) {
        return Instant.now().plus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
    }

    @Test
    void listsOnlyCallersApplications() throws Exception {
        String mine = application(nour, "INTERVIEW_SCHEDULED", "TECHNICAL");
        interview(mine, "TECHNICAL", inDays(3), "ONLINE", "https://meet.example.com/abc", "SCHEDULED");
        application(user(Role.CANDIDATE), "SHORTLISTED", null);

        mvc.perform(get("/api/portal/applications").header("Authorization", bearer(nour)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(mine))
                .andExpect(jsonPath("$.data.content[0].jobTitle").value("Senior Backend Engineer"))
                .andExpect(jsonPath("$.data.content[0].positionCode").value("BE-204"))
                .andExpect(jsonPath("$.data.content[0].stage").value("INTERVIEWING"))
                .andExpect(jsonPath("$.data.content[0].nextInterviewAt").exists());
    }

    @Test
    void pageSizeIsCappedAtFifty() throws Exception {
        application(nour, "SHORTLISTED", null);
        mvc.perform(get("/api/portal/applications?size=500").header("Authorization", bearer(nour)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(50));
    }

    @Test
    void otherCandidatesApplicationIsNotFound() throws Exception {
        String theirs = application(user(Role.CANDIDATE), "SHORTLISTED", null);

        String other = mvc.perform(get("/api/portal/applications/" + theirs).header("Authorization", bearer(nour)))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String random = mvc.perform(get("/api/portal/applications/" + UUID.randomUUID()).header("Authorization", bearer(nour)))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(other).isEqualTo(random);
    }

    @Test
    void staffCannotUseThePortalEndpoints() throws Exception {
        String id = application(nour, "SHORTLISTED", null);
        mvc.perform(get("/api/portal/applications").header("Authorization", bearer(recruiter))).andExpect(status().isForbidden());
        mvc.perform(get("/api/portal/applications/" + id).header("Authorization", bearer(recruiter))).andExpect(status().isForbidden());
        mvc.perform(get("/api/portal/applications/" + id)).andExpect(status().isUnauthorized());
    }

    @Test
    void newestApplicationComesFirstAndPagesMove() throws Exception {
        String older = application(nour, "SHORTLISTED", null);
        jdbc.update("UPDATE candidates SET created_at = now() - interval '2 days' WHERE id = ?", older);
        String newer = application(session("Platform Engineer", "PE-1"), nour, "SHORTLISTED", null);

        mvc.perform(get("/api/portal/applications?size=1").header("Authorization", bearer(nour)))
                .andExpect(jsonPath("$.data.content[0].id").value(newer))
                .andExpect(jsonPath("$.data.totalPages").value(2));
        mvc.perform(get("/api/portal/applications?size=1&page=1").header("Authorization", bearer(nour)))
                .andExpect(jsonPath("$.data.content[0].id").value(older));
    }

    @Test
    void offerInPreparationShowsAsDecisionPending() {
        assertThat(stageAt("OFFER_APPROVED")).isEqualTo(PortalStage.DECISION_PENDING);
        assertThat(stageAt("OFFER")).isEqualTo(PortalStage.DECISION_PENDING);
        assertThat(stageAt("OFFER_SENT")).isEqualTo(PortalStage.OFFER);
    }

    private PortalStage stageAt(String review) {
        return portal.application(nour.getEmail(), application(session("Senior Backend Engineer", "BE-" + review.length()), nour, review, null)).stage();
    }

    @Test
    void stepsFollowInterviewPlanOrder() {
        String id = application(nour, "INTERVIEW_SCHEDULED", "MANAGEMENT,TECHNICAL,HR");
        interview(id, "HR", inDays(-2), "HQ", null, "COMPLETED");
        interview(id, "TECHNICAL", inDays(2), "ONLINE", "https://meet.example.com/abc", "SCHEDULED");

        ApplicationView view = portal.application(nour.getEmail(), id);

        assertThat(view.steps()).extracting(s -> s.kind()).containsExactly(InterviewKind.HR, InterviewKind.TECHNICAL, InterviewKind.MANAGEMENT);
        assertThat(view.steps()).extracting(s -> s.state()).containsExactly(StepState.DONE, StepState.SCHEDULED, StepState.PLANNED);
    }

    @Test
    void cancelledInterviewShowsCancelled() {
        String id = application(nour, "INTERVIEW_SCHEDULED", "TECHNICAL");
        interview(id, "TECHNICAL", inDays(2), "HQ", null, "CANCELLED");

        ApplicationView view = portal.application(nour.getEmail(), id);

        assertThat(view.steps().get(0).state()).isEqualTo(StepState.CANCELLED);
        assertThat(view.interviews().get(0).status().name()).isEqualTo("CANCELLED");
    }

    @Test
    void aRebookedRoundShowsTheLiveInterview() {
        String id = application(nour, "INTERVIEW_SCHEDULED", "HR");
        interview(id, "HR", inDays(2), "HQ", null, "SCHEDULED");
        interview(id, "HR", inDays(6), "HQ", null, "CANCELLED");

        assertThat(portal.application(nour.getEmail(), id).steps().get(0).state()).isEqualTo(StepState.SCHEDULED);
    }

    @Test
    void everyReviewStatusHasAStage() {
        for (var status : com.resumescreener.common.session.ReviewStatus.values()) {
            assertThat(PortalStage.of(status)).as(status.name()).isNotNull();
        }
    }

    @Test
    void viewShowsTheJobInterviewerScheduleAndPlace() {
        String id = application(nour, "INTERVIEW_SCHEDULED", "TECHNICAL");
        Instant start = inDays(4);
        interview(id, "TECHNICAL", start, "ONLINE", "https://meet.example.com/abc", "SCHEDULED");

        ApplicationView view = portal.application(nour.getEmail(), id);

        assertThat(view.job().title()).isEqualTo("Senior Backend Engineer");
        assertThat(view.job().positionCode()).isEqualTo("BE-204");
        assertThat(view.job().description()).isEqualTo("Build the payments API");
        assertThat(view.job().requiredSkills()).containsExactly("Java", "Spring Boot");
        assertThat(view.recruiter().name()).isEqualTo(recruiter.getFullName());
        var card = view.interviews().get(0);
        assertThat(card.interviewerName()).isEqualTo(interviewer.getFullName());
        assertThat(card.interviewerHeadline()).isEqualTo("Senior Backend Engineer");
        assertThat(card.scheduledStart()).isEqualTo(start);
        assertThat(card.durationMinutes()).isEqualTo(45);
        assertThat(card.location()).isEqualTo("https://meet.example.com/abc");
    }

    @Test
    void viewUsesTwoQueries() throws Exception {
        String id = application(nour, "INTERVIEW_SCHEDULED", "TECHNICAL,HR");
        interview(id, "TECHNICAL", inDays(2), "HQ", null, "SCHEDULED");
        interview(id, "HR", inDays(5), "OTHER", "Cairo office, floor 3", "SCHEDULED");
        AtomicInteger statements = new AtomicInteger();
        PortalQueryService counted = new PortalQueryService(
                new PortalRepository(new NamedParameterJdbcTemplate(counting(dataSource, statements)), json), Clock.systemUTC());

        ApplicationView view = counted.application(nour.getEmail(), id);

        assertThat(view.interviews()).hasSize(2);
        assertThat(statements.get()).isEqualTo(2);
    }

    /** Counts the statements prepared on connections from {@code target}. */
    private static DataSource counting(DataSource target, AtomicInteger statements) {
        InvocationHandler onDataSource = (proxy, method, args) -> {
            Object result = invoke(target, method, args);
            if (!(result instanceof Connection connection)) return result;
            return Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (p, m, a) -> {
                if (m.getName().startsWith("prepare")) statements.incrementAndGet();
                return invoke(connection, m, a);
            });
        };
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[]{DataSource.class}, onDataSource);
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Test
    void jsonHasNoInternalFields() throws Exception {
        String id = application(nour, "INTERVIEW_SCHEDULED", "TECHNICAL");
        interview(id, "TECHNICAL", inDays(2), "HQ", null, "COMPLETED");

        String body = mvc.perform(get("/api/portal/applications/" + id).header("Authorization", bearer(nour)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("rating", "assessment", "Solid", "compositeScore", "82.4", "noteToInterviewer", "Probe the gaps",
                "resume", "justification", "@");
    }
}
