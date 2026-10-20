package com.resumescreener.api.admin;

import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.RoleRepository;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V18: the role catalogue, against real Postgres. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RoleCatalogueSchemaTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired RoleRepository roles;

    @Test
    void seedMatchesEnum() {
        RoleCatalogueCheck.verify(roles.storedCodesAndKinds());
        assertThat(roles.findById(Role.TECHNICAL_INTERVIEWER).orElseThrow().getName()).isEqualTo("Technical interviewer");
        assertThat(roles.findById(Role.CANDIDATE).orElseThrow().getKind()).isEqualTo(Role.Kind.CANDIDATE);
    }

    @Test
    void userWithUnknownRoleIsRejectedByForeignKey() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO users (id, email, password_hash, full_name, role, created_at) VALUES (?, ?, 'h', 'X', 'JANITOR', now())",
                UUID.randomUUID().toString(), UUID.randomUUID() + "@x.com"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void candidateStillCannotHoldPermissions() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO role_permissions (role, permission) VALUES ('CANDIDATE', 'SESSION_RUN')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void roleNamesAreUniqueIgnoringCase() {
        assertThatThrownBy(() -> jdbc.update("UPDATE roles SET name = 'RECRUITER' WHERE code = 'CEO'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bootFailsWhenEnumAndTableDisagree() {
        List<String> missingOne = roles.storedCodesAndKinds().stream().filter(c -> !c.startsWith("CEO:")).toList();
        assertThatThrownBy(() -> RoleCatalogueCheck.verify(missingOne)).isInstanceOf(IllegalStateException.class).hasMessageContaining("CEO");

        List<String> wrongKind = roles.storedCodesAndKinds().stream().map(c -> c.equals("RECRUITER:STAFF") ? "RECRUITER:CANDIDATE" : c).toList();
        assertThatThrownBy(() -> RoleCatalogueCheck.verify(wrongKind)).isInstanceOf(IllegalStateException.class);
    }
}
