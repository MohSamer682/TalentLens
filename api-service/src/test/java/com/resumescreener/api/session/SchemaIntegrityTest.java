package com.resumescreener.api.session;

import com.resumescreener.common.session.ScreeningSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The Flyway schema itself: cascades, constraints and indexes, against real Postgres. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class SchemaIntegrityTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ScreeningSessionRepository sessions;

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO users (id, email, password_hash, full_name, role, created_at) VALUES ('u1','a@x.com','h','A','RECRUITER', now())");
        jdbc.update("INSERT INTO screening_sessions (id, user_id, title, status, created_at) VALUES ('s1','u1','t','DONE', now())");
        jdbc.update("INSERT INTO scoring_weights (id, session_id, technical_skills) VALUES ('w1','s1',100)");
        jdbc.update("INSERT INTO candidates (id, session_id, status) VALUES ('c1','s1','SCORED')");
        jdbc.update("INSERT INTO candidate_texts (candidate_id, raw_text) VALUES ('c1','text')");
        jdbc.update("INSERT INTO candidate_scores (id, candidate_id, composite_score) VALUES ('sc1','c1',80)");
        jdbc.update("INSERT INTO batch_jobs (id, session_id) VALUES ('j1','s1')");
        jdbc.update("INSERT INTO candidate_interviews (id, candidate_id, kind, interviewer_id, scheduled_start, created_by)"
                + " VALUES ('i1','c1','HR','u1', now(), 'u1')");
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    @Test
    void deletingASessionRemovesTheWholeAggregateInOneStatement() {
        assertThat(sessions.deleteByIdDirect("s1")).isEqualTo(1);

        for (String table : new String[]{"scoring_weights", "candidates", "candidate_texts", "candidate_scores", "batch_jobs", "candidate_interviews"}) {
            assertThat(count(table)).as(table).isZero();
        }
        assertThat(count("users")).isEqualTo(1);
    }

    @Test
    void emailIsUniqueIgnoringCase() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO users (id, email, password_hash, full_name, role, created_at) VALUES ('u2','A@X.com','h','B','RECRUITER', now())"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void scoreAbove100IsRejected() {
        jdbc.update("INSERT INTO candidates (id, session_id, status) VALUES ('c2','s1','SCORED')");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO candidate_scores (id, candidate_id, composite_score) VALUES ('sc2','c2',101)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // one violation per test: Postgres aborts the surrounding transaction after the first error
    @Test
    void ratingAbove5IsRejected() {
        assertThatThrownBy(() -> jdbc.update("UPDATE candidate_interviews SET rating = 6 WHERE id = 'i1'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unknownReviewStatusIsRejected() {
        assertThatThrownBy(() -> jdbc.update("UPDATE candidates SET review_status = 'BOGUS' WHERE id = 'c1'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void auditActorIsNulledWhenTheUserGoes() {
        jdbc.update("INSERT INTO audit_events (id, occurred_at, actor_user_id, action) VALUES ('a1', now(), 'u1', 'X')");
        jdbc.update("DELETE FROM candidate_interviews");
        jdbc.update("DELETE FROM screening_sessions");
        jdbc.update("DELETE FROM users WHERE id = 'u1'");
        assertThat(jdbc.queryForObject("SELECT actor_user_id FROM audit_events WHERE id = 'a1'", String.class)).isNull();
    }
}
