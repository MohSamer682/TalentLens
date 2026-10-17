package com.resumescreener.api.admin;

import com.resumescreener.api.audit.AuditService;
import com.resumescreener.common.user.Permission;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.RolePermission;
import com.resumescreener.common.user.RolePermissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Against the real V7 seed: replacing grants must leave exactly the new set in the table. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RoleService.class)
@Testcontainers
class RoleServiceDatabaseTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired RoleService service;
    @Autowired RolePermissionRepository grants;
    @MockBean AuditService audit;

    @Test
    void replacingGrantsKeepsTheOnesThatStay() {
        service.update(Role.HR_CHIEF, Set.of(Permission.SESSION_RUN, Permission.SESSION_VIEW_ALL, Permission.CANDIDATE_REVIEW));

        assertThat(grants.findByRole(Role.HR_CHIEF)).extracting(RolePermission::getPermission)
                .containsExactlyInAnyOrder(Permission.SESSION_RUN, Permission.SESSION_VIEW_ALL, Permission.CANDIDATE_REVIEW);
    }

    @Test
    void seedMatchesTheDefaults() {
        assertThat(grants.findByRole(Role.RECRUITER)).extracting(RolePermission::getPermission)
                .containsExactlyInAnyOrder(Permission.SESSION_RUN, Permission.CANDIDATE_REVIEW);
        assertThat(service.catalog().roles()).hasSize(3);
    }
}
