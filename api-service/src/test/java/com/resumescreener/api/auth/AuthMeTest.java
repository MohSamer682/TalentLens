package com.resumescreener.api.auth;

import com.resumescreener.common.user.Permission;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.RolePermission;
import com.resumescreener.common.user.RolePermissionRepository;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthMeTest {

    private final UserRepository users = mock(UserRepository.class);
    private final RolePermissionRepository grants = mock(RolePermissionRepository.class);
    private final AuthService auth = new AuthService(users, null, null, null, new UserDetailsServiceImpl(users, grants));

    @Test
    void signedInUserCarriesSortedPermissions() {
        user(Role.RECRUITER);
        when(grants.findByRole(Role.RECRUITER)).thenReturn(List.of(
                new RolePermission(Role.RECRUITER, Permission.SESSION_RUN),
                new RolePermission(Role.RECRUITER, Permission.CANDIDATE_REVIEW)));

        assertThat(auth.me("u@example.com").getPermissions()).containsExactly("CANDIDATE_REVIEW", "SESSION_RUN");
    }

    @Test
    void meReflectsChangedRole() {
        user(Role.RECRUITER);
        assertThat(auth.me("u@example.com").getRole()).isEqualTo("RECRUITER");

        user(Role.ADMIN);

        assertThat(auth.me("u@example.com").getRole()).isEqualTo("ADMIN");
        assertThat(auth.me("u@example.com").getPermissions()).hasSize(Permission.values().length);
    }

    private void user(Role role) {
        when(users.findByEmailIgnoreCase("u@example.com"))
                .thenReturn(Optional.of(User.builder().id("u").email("u@example.com").fullName("U").role(role).build()));
    }
}
