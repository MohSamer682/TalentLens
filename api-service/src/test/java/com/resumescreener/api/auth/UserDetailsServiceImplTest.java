package com.resumescreener.api.auth;

import com.resumescreener.common.user.Permission;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.RolePermission;
import com.resumescreener.common.user.RolePermissionRepository;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserDetailsServiceImplTest {

    private final UserRepository users = mock(UserRepository.class);
    private final RolePermissionRepository grants = mock(RolePermissionRepository.class);
    private final UserDetailsServiceImpl service = new UserDetailsServiceImpl(users, grants);

    @Test
    void authoritiesAreRolePlusGrants() {
        signedIn(Role.RECRUITER);
        grant(Role.RECRUITER, Permission.SESSION_RUN, Permission.CANDIDATE_REVIEW);

        assertThat(authorities()).containsExactlyInAnyOrder("ROLE_RECRUITER", "SESSION_RUN", "CANDIDATE_REVIEW");
    }

    @Test
    void authoritiesAreReloadedPerRequest() {
        signedIn(Role.RECRUITER);
        grant(Role.RECRUITER, Permission.SESSION_RUN);
        assertThat(authorities()).contains("SESSION_RUN");

        grant(Role.RECRUITER);

        assertThat(authorities()).containsExactly("ROLE_RECRUITER");
    }

    @Test
    void adminOnlyGrantsInTheTableAreIgnored() {
        signedIn(Role.HR_CHIEF);
        grant(Role.HR_CHIEF, Permission.ROLE_MANAGE, Permission.ANALYTICS_VIEW);

        assertThat(authorities()).containsExactlyInAnyOrder("ROLE_HR_CHIEF", "ANALYTICS_VIEW");
    }

    private void signedIn(Role role) {
        when(users.findByEmailIgnoreCase("u@example.com"))
                .thenReturn(Optional.of(User.builder().email("u@example.com").passwordHash("x").role(role).build()));
    }

    private void grant(Role role, Permission... permissions) {
        when(grants.findByRole(role)).thenReturn(java.util.Arrays.stream(permissions).map(p -> new RolePermission(role, p)).toList());
    }

    private List<String> authorities() {
        return service.loadUserByUsername("u@example.com").getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }
}
