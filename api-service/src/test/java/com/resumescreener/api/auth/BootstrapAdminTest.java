package com.resumescreener.api.auth;

import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BootstrapAdminTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired UserRepository users;

    private BootstrapAdmin bootstrap(String email) {
        return new BootstrapAdmin(users, new BCryptPasswordEncoder(), email, "FirstPass2026");
    }

    @Test
    void bootstrapAdminIsCreatedOnceWhenNoAdminExists() {
        users.deleteAll();
        assertThat(bootstrap("Owner@TalentLens.io").ensureAdmin()).isTrue();

        User admin = users.findByEmailIgnoreCase("owner@talentlens.io").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isMustChangePassword()).isTrue();
        assertThat(new BCryptPasswordEncoder().matches("FirstPass2026", admin.getPasswordHash())).isTrue();

        assertThat(bootstrap("Owner@TalentLens.io").ensureAdmin()).isFalse();
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void bootstrapAdminIsSkippedWhenAnAdminExists() {
        users.deleteAll();
        users.save(User.builder().email("first@talentlens.io").fullName("First").passwordHash("h").role(Role.ADMIN).build());

        assertThat(bootstrap("second@talentlens.io").ensureAdmin()).isFalse();
        assertThat(users.findByEmailIgnoreCase("second@talentlens.io")).isEmpty();
        assertThat(bootstrap("").ensureAdmin()).isFalse();
    }

    @Test
    void bootstrapAdminRefusesAWeakPassword() {
        users.deleteAll();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new BootstrapAdmin(users, new BCryptPasswordEncoder(), "owner@talentlens.io", "short").ensureAdmin())
                .isInstanceOf(IllegalStateException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new BootstrapAdmin(users, new BCryptPasswordEncoder(), "owner@talentlens.io", "change-me-on-first-sign-in").ensureAdmin())
                .isInstanceOf(IllegalStateException.class);
        assertThat(users.count()).isZero();
    }
}
