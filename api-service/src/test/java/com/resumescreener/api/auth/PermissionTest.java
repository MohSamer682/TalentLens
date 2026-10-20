package com.resumescreener.api.auth;

import com.resumescreener.common.user.Permission;
import com.resumescreener.common.user.Role;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionTest {

    @Test
    void adminAlwaysHasEverything() {
        assertThat(Permission.effective(Role.ADMIN, Set.of())).isEqualTo(EnumSet.allOf(Permission.class));
    }

    @Test
    void adminOnlyPermissionsAreStripped() {
        assertThat(Permission.effective(Role.HR_CHIEF, Set.of(Permission.ROLE_MANAGE, Permission.ANALYTICS_VIEW)))
                .containsExactly(Permission.ANALYTICS_VIEW);
    }

    @Test
    void candidatesNeverHoldPermissions() {
        assertThat(Permission.effective(Role.CANDIDATE, Set.of(Permission.SESSION_RUN, Permission.INTERVIEW_FEEDBACK))).isEmpty();
    }

    @Test
    void everyPermissionHasADescription() {
        for (Permission p : Permission.values()) {
            assertThat(p.description()).isNotBlank();
        }
    }
}
