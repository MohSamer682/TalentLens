package com.resumescreener.api.jobs;

import com.resumescreener.common.session.ScreeningSession;
import com.resumescreener.common.session.ScreeningSessionRepository;
import com.resumescreener.common.session.SessionStatus;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserRepository;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V14: job details and required skills on screening sessions, against real Postgres. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JobDetailsSchemaTest {

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
    @Autowired UserRepository users;
    @Autowired TransactionTemplate tx;

    private ScreeningSession newSession(List<String> skills) {
        User owner = users.save(User.builder().email(java.util.UUID.randomUUID() + "@x.com").passwordHash("h")
                .fullName("Owner").role(Role.RECRUITER).createdAt(Instant.now()).build());
        return ScreeningSession.builder().user(owner).title("Backend").jobTitle("Senior Backend Engineer")
                .positionCode("ENG-BE-104").status(SessionStatus.PENDING)
                .requiredSkills(new java.util.ArrayList<>(skills)).build();
    }

    @Test
    void skillsPersistInOrderAndReloadIdentically() {
        String id = tx.execute(s -> sessions.save(newSession(List.of("Java", "Spring", "SQL"))).getId());

        List<String> reloaded = tx.execute(s -> List.copyOf(sessions.findById(id).orElseThrow().getRequiredSkills()));

        assertThat(reloaded).containsExactly("Java", "Spring", "SQL");
        assertThat(jdbc.queryForList("SELECT skill FROM session_required_skills WHERE session_id = ? ORDER BY position",
                String.class, id)).containsExactly("Java", "Spring", "SQL");
    }

    @Test
    void replacingSkillsInANewOrderRewritesThem() {
        String id = tx.execute(s -> sessions.save(newSession(List.of("Java", "Spring"))).getId());

        tx.executeWithoutResult(s -> sessions.findById(id).orElseThrow()
                .setRequiredSkills(new java.util.ArrayList<>(List.of("Spring", "Java", "Kafka"))));

        assertThat(jdbc.queryForList("SELECT skill FROM session_required_skills WHERE session_id = ? ORDER BY position",
                String.class, id)).containsExactly("Spring", "Java", "Kafka");
    }

    @Test
    void duplicateSkillIgnoringCaseIsRejected() {
        assertThatThrownBy(() -> tx.execute(s -> sessions.saveAndFlush(newSession(List.of("Java", "java")))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lowercasePositionCodeIsRejectedByCheck() {
        String id = tx.execute(s -> sessions.save(newSession(List.of("Java"))).getId());
        assertThatThrownBy(() -> jdbc.update("UPDATE screening_sessions SET position_code = 'be-1' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sessionWithoutJobTitleTakesItsTitle() {
        ScreeningSession session = newSession(List.of("Java"));
        session.setJobTitle(null);
        String id = tx.execute(s -> sessions.save(session).getId());

        assertThat(jdbc.queryForObject("SELECT job_title FROM screening_sessions WHERE id = ?", String.class, id))
                .isEqualTo("Backend");
    }

    @Test
    void existingSessionsAreBackfilledWithTitle() {
        jdbc.execute("CREATE SCHEMA backfill");
        FluentConfiguration flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("backfill").locations("classpath:db/migration");
        flyway.target("13").load().migrate();
        jdbc.update("INSERT INTO backfill.users (id, email, password_hash, full_name, role, created_at) VALUES ('u1','a@x.com','h','A','RECRUITER', now())");
        jdbc.update("INSERT INTO backfill.screening_sessions (id, user_id, title, status, created_at) VALUES ('s1','u1','Old cycle','DONE', now())");

        flyway.target("latest").load().migrate();

        assertThat(jdbc.queryForObject("SELECT job_title FROM backfill.screening_sessions WHERE id = 's1'", String.class))
                .isEqualTo("Old cycle");
        assertThat(jdbc.queryForObject("SELECT position_code FROM backfill.screening_sessions WHERE id = 's1'", String.class))
                .isNull();
    }
}
