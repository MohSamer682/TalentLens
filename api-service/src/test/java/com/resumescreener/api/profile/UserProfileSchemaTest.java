package com.resumescreener.api.profile;

import com.resumescreener.common.user.ProfileFields;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserProfile;
import com.resumescreener.common.user.UserProfileRepository;
import com.resumescreener.common.user.UserRepository;
import com.resumescreener.common.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V15: user status, version and profiles, against real Postgres. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserProfileSchemaTest {

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
    @Autowired UserProfileRepository profiles;
    @Autowired TransactionTemplate tx;
    @Autowired org.springframework.transaction.PlatformTransactionManager txManager;

    private User newUser() {
        return users.save(User.builder().email(UUID.randomUUID() + "@x.com").passwordHash("h")
                .fullName("Karim Adel").role(Role.RECRUITER).createdAt(Instant.now()).build());
    }

    @Test
    void newUserIsActiveWithNoForcedPasswordChange() {
        User user = newUser();
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, user.getId())).isEqualTo("ACTIVE");
        assertThat(users.findById(user.getId()).orElseThrow().isMustChangePassword()).isFalse();
    }

    @Test
    void profileIsSavedAndDeletedWithUser() {
        User user = newUser();
        tx.executeWithoutResult(s -> { UserProfile p = UserProfile.blankFor(users.getReferenceById(user.getId())); p.update(new ProfileFields(null, "Senior Recruiter", null, null, null), user.getId(), Instant.now()); profiles.save(p); });
        assertThat(profiles.findById(user.getId()).orElseThrow().getHeadline()).isEqualTo("Senior Recruiter");

        jdbc.update("DELETE FROM users WHERE id = ?", user.getId());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_profiles WHERE user_id = ?", Integer.class, user.getId())).isZero();
    }

    @Test
    void statusCheckRejectsUnknownValue() {
        User user = newUser();
        assertThatThrownBy(() -> jdbc.update("UPDATE users SET status = 'DELETED' WHERE id = ?", user.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void concurrentEditsBumpVersionAndSecondSaveFails() {
        User user = newUser();
        User first = users.findById(user.getId()).orElseThrow();
        User second = users.findById(user.getId()).orElseThrow();

        first.setFullName("First edit");
        users.saveAndFlush(first);
        second.setFullName("Second edit");

        assertThatThrownBy(() -> users.saveAndFlush(second)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(users.findById(user.getId()).orElseThrow().getFullName()).isEqualTo("First edit");
    }

    @Test
    void emailIsUniqueIgnoringCaseInTheDatabase() {
        String email = "Case-" + UUID.randomUUID() + "@t.io";
        jdbc.update("INSERT INTO users (id, email, password_hash, full_name, role, created_at) VALUES (?, ?, 'h', 'A', 'RECRUITER', now())",
                UUID.randomUUID().toString(), email);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO users (id, email, password_hash, full_name, role, created_at) VALUES (?, ?, 'h', 'B', 'RECRUITER', now())",
                UUID.randomUUID().toString(), email.toLowerCase())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lockingActiveAdminsMakesASecondGuardWait() {
        User admin = users.save(User.builder().email("lock-" + UUID.randomUUID() + "@t.io").fullName("L").passwordHash("h").role(Role.ADMIN).build());

        tx.executeWithoutResult(s -> {
            assertThat(users.lockByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)).extracting(User::getId).contains(admin.getId());
            TransactionTemplate separate = new TransactionTemplate(txManager);
            separate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
            assertThatThrownBy(() -> separate.executeWithoutResult(other ->
                    jdbc.queryForList("SELECT id FROM users WHERE id = ? FOR UPDATE NOWAIT", admin.getId())))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
        });
    }

    @Test
    void hidingIsStored() {
        User user = newUser();
        User loaded = users.findById(user.getId()).orElseThrow();
        loaded.hide();
        users.saveAndFlush(loaded);
        assertThat(users.findById(user.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.HIDDEN);
    }

    @Test
    void deleteOfUserWhoOwnsASessionIsRefusedByForeignKey() {
        User user = newUser();
        jdbc.update("INSERT INTO screening_sessions (id, user_id, title, job_title, status, created_at) VALUES (?, ?, 't', 't', 'DONE', now())",
                UUID.randomUUID().toString(), user.getId());

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            users.deleteById(user.getId());
            users.flush();
        })).isInstanceOf(DataIntegrityViolationException.class);
    }
}
