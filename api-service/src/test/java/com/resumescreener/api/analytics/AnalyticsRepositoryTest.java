package com.resumescreener.api.analytics;

import com.resumescreener.api.analytics.dto.AnalyticsOverview;
import com.resumescreener.api.analytics.dto.AnalyticsOverview.ScoreBand;
import com.resumescreener.common.session.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// Not the app class: its @EnableJpaRepositories needs JPA, which a JDBC slice does not start
@ContextConfiguration(classes = AnalyticsRepository.class)
@Testcontainers
class AnalyticsRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired AnalyticsRepository repository;
    @Autowired JdbcTemplate jdbc;

    private final Instant to = Instant.now().truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS);
    private final Instant from = to.minus(90, ChronoUnit.DAYS);
    private final Instant recent = to.minus(10, ChronoUnit.DAYS);

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE candidate_scores, candidates, scoring_weights, screening_sessions, users CASCADE");
    }

    @Test
    void emptyPeriodReturnsZeros() {
        AnalyticsOverview o = repository.overview(from, to);

        assertThat(o.totals().sessions()).isZero();
        assertThat(o.totals().resumesUploaded()).isZero();
        assertThat(o.totals().shortlistRate()).isNull();
        assertThat(o.totals().medianHoursToRanked()).isNull();
        assertThat(o.totals().sessionsByStatus()).containsEntry(SessionStatus.DONE, 0L);
        assertThat(o.scoreBands()).extracting(ScoreBand::count).containsExactly(0L, 0L, 0L, 0L, 0L);
        assertThat(o.recruiters()).isEmpty();
        assertThat(o.weeks()).hasSizeBetween(13, 15).allMatch(w -> w.sessionsCreated() == 0 && w.resumesScored() == 0);
    }

    @Test
    void countsResumesByProcessingStatus() {
        String s = session(user("Rana"), "DONE", recent, Duration.ofHours(1));
        for (int i = 0; i < 5; i++) candidate(s, "SCORED", "NONE", 70.0);
        candidate(s, "FAILED", "NONE", null);
        candidate(s, "FAILED", "NONE", null);
        candidate(s, "PENDING", "NONE", null);

        AnalyticsOverview.Totals t = repository.overview(from, to).totals();

        assertThat(t.resumesUploaded()).isEqualTo(8);
        assertThat(t.resumesScored()).isEqualTo(5);
        assertThat(t.resumesUnreadable()).isEqualTo(2);
    }

    @Test
    void shortlistRateIsShortlistedOverScored() {
        String s = session(user("Rana"), "DONE", recent, Duration.ofHours(1));
        candidate(s, "SCORED", "SHORTLISTED", 90.0);
        candidate(s, "SCORED", "SHORTLISTED", 80.0);
        for (int i = 0; i < 3; i++) candidate(s, "SCORED", "NONE", 50.0);

        assertThat(repository.overview(from, to).totals().shortlistRate()).isEqualTo(0.4);
    }

    @Test
    void medianIgnoresUnfinishedSessions() {
        String u = user("Rana");
        session(u, "DONE", recent, Duration.ofHours(2));
        session(u, "DONE", recent, Duration.ofHours(4));
        session(u, "DONE", recent, Duration.ofHours(10));
        session(u, "RUNNING", recent, null);

        AnalyticsOverview o = repository.overview(from, to);

        assertThat(o.totals().medianHoursToRanked()).isEqualTo(4.0);
        assertThat(o.totals().sessions()).isEqualTo(4);
        assertThat(o.recruiters().get(0).medianHoursToRanked()).isEqualTo(4.0);
    }

    @Test
    void excludesSessionsOutsidePeriod() {
        session(user("Rana"), "DONE", to.minus(100, ChronoUnit.DAYS), Duration.ofHours(1));

        assertThat(repository.overview(from, to).totals().sessions()).isZero();
    }

    @Test
    void scoreBandsUseInclusiveLowerBounds() {
        String s = session(user("Rana"), "DONE", recent, Duration.ofHours(1));
        for (double score : new double[]{39.9, 40, 74.99, 90}) candidate(s, "SCORED", "NONE", score);

        assertThat(repository.overview(from, to).scoreBands()).extracting(ScoreBand::count)
                .containsExactly(1L, 1L, 1L, 0L, 1L);
    }

    @Test
    void recruiterTableListsOnlyRecruitersWithSessions() {
        String rana = user("Rana");
        user("Idle recruiter");
        String s = session(rana, "DONE", recent, Duration.ofHours(3));
        candidate(s, "SCORED", "SHORTLISTED", 88.0);
        candidate(s, "SCORED", "NONE", 60.0);

        assertThat(repository.overview(from, to).recruiters()).singleElement().satisfies(r -> {
            assertThat(r.fullName()).isEqualTo("Rana");
            assertThat(r.sessions()).isEqualTo(1);
            assertThat(r.resumesScored()).isEqualTo(2);
            assertThat(r.shortlisted()).isEqualTo(1);
        });
    }

    @Test
    void weeksCarryActivityInTheWeekTheSessionStarted() {
        String s = session(user("Rana"), "DONE", recent, Duration.ofHours(1));
        candidate(s, "SCORED", "SHORTLISTED", 80.0);

        assertThat(repository.overview(from, to).weeks())
                .filteredOn(w -> w.sessionsCreated() > 0)
                .singleElement()
                .satisfies(w -> {
                    assertThat(w.resumesScored()).isEqualTo(1);
                    assertThat(w.shortlisted()).isEqualTo(1);
                });
    }

    @Test
    void calibrationByBand() {
        String u = user("Rana");
        String s = session(u, "DONE", recent, Duration.ofHours(1));
        String c = candidateWithId(s, "SCORED", "SHORTLISTED", 92.0);
        interview(c, u, "COMPLETED", "HIRE");

        ScoreBand topBand = repository.overview(from, to).scoreBands().get(4);
        assertThat(topBand.label()).isEqualTo("90–100");
        assertThat(topBand.count()).isEqualTo(1L);
        assertThat(topBand.shortlistRate()).isEqualTo(1.0);
        assertThat(topBand.hireRate()).isEqualTo(1.0);
        assertThat(topBand.interviewed()).isEqualTo(1L);
    }

    private String user(String name) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO users (id, email, password_hash, full_name, role, created_at) VALUES (?, ?, 'x', ?, 'RECRUITER', now())",
                id, id + "@example.com", name);
        return id;
    }

    private String session(String userId, String status, Instant created, Duration toRanked) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO screening_sessions (id, user_id, title, job_title, status, created_at, completed_at) VALUES (?, ?, 't', 't', ?, ?, ?)",
                id, userId, status, Timestamp.from(created), toRanked == null ? null : Timestamp.from(created.plus(toRanked)));
        return id;
    }

    private void candidate(String sessionId, String status, String review, Double composite) {
        candidateWithId(sessionId, status, review, composite);
    }

    private String candidateWithId(String sessionId, String status, String review, Double composite) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO candidates (id, session_id, status, review_status) VALUES (?, ?, ?, ?)", id, sessionId, status, review);
        if (composite != null) {
            jdbc.update("INSERT INTO candidate_scores (id, candidate_id, composite_score) VALUES (?, ?, ?)",
                    UUID.randomUUID().toString(), id, composite);
        }
        return id;
    }

    private void interview(String candidateId, String interviewerId, String status, String recommendation) {
        jdbc.update("""
                INSERT INTO candidate_interviews (id, candidate_id, kind, interviewer_id, scheduled_start,
                                                 status, recommendation, created_by, feedback_submitted_at)
                VALUES (?, ?, 'TECHNICAL', ?, now(), ?, ?, ?, now())
                """, UUID.randomUUID().toString(), candidateId, interviewerId, status, recommendation, interviewerId);
    }
}
