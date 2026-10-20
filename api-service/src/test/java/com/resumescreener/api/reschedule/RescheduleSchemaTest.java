package com.resumescreener.api.reschedule;

import com.resumescreener.common.reschedule.InterviewRescheduleRequest;
import com.resumescreener.common.reschedule.InterviewRescheduleRequestRepository;
import com.resumescreener.common.reschedule.RescheduleStatus;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserRepository;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V17: reschedule requests and their proposed times, against real Postgres. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RescheduleSchemaTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired InterviewRescheduleRequestRepository requests;

    private User user(Role role) {
        return users.save(User.builder().email(UUID.randomUUID() + "@x.com").passwordHash("h")
                .fullName("Nour Hassan").role(role).createdAt(Instant.now()).build());
    }

    /** An interview with its session and candidate; returns the interview id. */
    private String interview(User owner) {
        String session = UUID.randomUUID().toString();
        String candidate = UUID.randomUUID().toString();
        String interview = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO screening_sessions (id, user_id, title, job_title, status, created_at) VALUES (?, ?, 't', 't', 'DONE', now())", session, owner.getId());
        jdbc.update("INSERT INTO candidates (id, session_id, status, review_status) VALUES (?, ?, 'SCORED', 'INTERVIEW_SCHEDULED')", candidate, session);
        jdbc.update("INSERT INTO candidate_interviews (id, candidate_id, kind, interviewer_id, scheduled_start, created_by) "
                + "VALUES (?, ?, 'HR', ?, now() + interval '2 days', ?)", interview, candidate, owner.getId(), owner.getId());
        return interview;
    }

    private InterviewRescheduleRequest open(String interviewId, User by) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return InterviewRescheduleRequest.open(interviewId, by.getId(), "Exam that morning",
                List.of(now.plus(3, ChronoUnit.DAYS), now.plus(4, ChronoUnit.DAYS)), now);
    }

    @Test
    void slotsAreStoredInOrderFromOne() {
        User hr = user(Role.RECRUITER);
        InterviewRescheduleRequest saved = requests.saveAndFlush(open(interview(hr), user(Role.CANDIDATE)));

        assertThat(jdbc.queryForList("SELECT slot_no FROM interview_reschedule_slots WHERE request_id = ? ORDER BY slot_no", Integer.class, saved.getId()))
                .containsExactly(1, 2);
        assertThat(requests.findById(saved.getId()).orElseThrow().getStatus()).isEqualTo(RescheduleStatus.PENDING);
    }

    @Test
    void secondPendingRequestIsRejectedByTheDatabase() {
        User hr = user(Role.RECRUITER);
        User nour = user(Role.CANDIDATE);
        String interviewId = interview(hr);
        requests.saveAndFlush(open(interviewId, nour));

        assertThatThrownBy(() -> requests.saveAndFlush(open(interviewId, nour))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anAnsweredRequestMakesRoomForANewOne() {
        User hr = user(Role.RECRUITER);
        User nour = user(Role.CANDIDATE);
        String interviewId = interview(hr);
        InterviewRescheduleRequest first = requests.saveAndFlush(open(interviewId, nour));
        first.withdraw(Instant.now());
        requests.saveAndFlush(first);

        assertThat(requests.saveAndFlush(open(interviewId, nour)).getId()).isNotEqualTo(first.getId());
    }

    @Test
    void resolvedAtMatchesStatusCheck() {
        User hr = user(Role.RECRUITER);
        String id = requests.saveAndFlush(open(interview(hr), user(Role.CANDIDATE))).getId();

        assertThatThrownBy(() -> jdbc.update("UPDATE interview_reschedule_requests SET status = 'ACCEPTED' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE interview_reschedule_requests SET resolved_at = now() WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
