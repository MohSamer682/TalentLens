package com.resumescreener.api.portal;

import com.resumescreener.common.portal.CandidateInvite;
import com.resumescreener.common.portal.CandidateInviteRepository;
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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V16: candidate accounts and access links, against real Postgres. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CandidateAccountsSchemaTest {

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
    @Autowired CandidateInviteRepository invites;

    private User user(Role role) {
        return users.save(User.builder().email(UUID.randomUUID() + "@x.com").passwordHash("h")
                .fullName("Nour Hassan").role(role).createdAt(Instant.now()).build());
    }

    private String session(User owner) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO screening_sessions (id, user_id, title, job_title, status, created_at) VALUES (?, ?, 't', 't', 'DONE', now())",
                id, owner.getId());
        return id;
    }

    private String candidate(String sessionId) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO candidates (id, session_id, status, review_status) VALUES (?, ?, 'SCORED', 'SHORTLISTED')", id, sessionId);
        return id;
    }

    private CandidateInvite invite(String candidateId, User by, Duration ttl) {
        Instant now = Instant.now();
        return CandidateInvite.builder().candidateId(candidateId).tokenHash(UUID.randomUUID().toString().replace("-", "") + "0".repeat(32))
                .createdBy(by.getId()).createdAt(now).expiresAt(now.plus(ttl)).build();
    }

    private CandidateInvite invite(String candidateId, User by) {
        return invite(candidateId, by, Duration.ofDays(7));
    }

    @Test
    void candidateRoleIsAccepted() {
        User candidate = user(Role.CANDIDATE);
        assertThat(users.findById(candidate.getId()).orElseThrow().getRole()).isEqualTo(Role.CANDIDATE);
    }

    @Test
    void candidateRoleCannotBeGrantedPermissions() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO role_permissions (role, permission) VALUES ('CANDIDATE', 'SESSION_RUN')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyOneOpenInvitePerCandidate() {
        User hr = user(Role.RECRUITER);
        String candidateId = candidate(session(hr));
        CandidateInvite first = invites.saveAndFlush(invite(candidateId, hr));

        assertThatThrownBy(() -> invites.saveAndFlush(invite(candidateId, hr))).isInstanceOf(DataIntegrityViolationException.class);

        first.revoke(Instant.now());
        invites.saveAndFlush(first);
        invites.saveAndFlush(invite(candidateId, hr));
        assertThat(invites.count()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void inviteMustExpireAfterItIsCreated() {
        User hr = user(Role.RECRUITER);
        CandidateInvite bad = invite(candidate(session(hr)), hr, Duration.ZERO);
        assertThatThrownBy(() -> invites.saveAndFlush(bad)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void oneApplicationPerAccountPerSession() {
        User hr = user(Role.RECRUITER);
        User account = user(Role.CANDIDATE);
        String sessionId = session(hr);
        String first = candidate(sessionId);
        String second = candidate(sessionId);
        jdbc.update("UPDATE candidates SET account_user_id = ? WHERE id = ?", account.getId(), first);

        assertThatThrownBy(() -> jdbc.update("UPDATE candidates SET account_user_id = ? WHERE id = ?", account.getId(), second))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("UPDATE candidates SET account_user_id = ? WHERE id = ?", account.getId(), candidate(session(hr)));
    }

    @Test
    void deletingTheAccountUnlinksTheApplication() {
        User hr = user(Role.RECRUITER);
        User account = user(Role.CANDIDATE);
        String candidateId = candidate(session(hr));
        jdbc.update("UPDATE candidates SET account_user_id = ? WHERE id = ?", account.getId(), candidateId);

        users.deleteById(account.getId());
        assertThat(jdbc.queryForObject("SELECT account_user_id FROM candidates WHERE id = ?", String.class, candidateId)).isNull();
    }
}
