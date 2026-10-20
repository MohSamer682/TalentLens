package com.resumescreener.api.profile;

import com.resumescreener.api.common.ApiException;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserRepository;
import com.resumescreener.common.user.UserStatus;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminUserGuardsTest {

    private final UserRepository users = mock(UserRepository.class);
    private final AdminUserService service = new AdminUserService(users, null, null, null, null, null);
    private final User caller = User.builder().id("caller").email("c@t.io").role(Role.ADMIN).build();
    private final User target = User.builder().id("target").email("t@t.io").role(Role.ADMIN).build();

    @Test
    void theLastActiveAdminCannotBeHiddenDemotedOrDeleted() {
        when(users.lockByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)).thenReturn(java.util.List.of(new User()));

        assertThatThrownBy(() -> service.guardLosingAdmin(target))
                .isInstanceOf(ApiException.class).hasMessage("At least one active admin must remain");
    }

    @Test
    void anotherActiveAdminLetsItThrough() {
        when(users.lockByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)).thenReturn(java.util.List.of(new User(), new User()));
        assertThatCode(() -> service.guardLosingAdmin(target)).doesNotThrowAnyException();
    }

    @Test
    void aHiddenAdminIsNotCountedAsTheLastOne() {
        target.hide();
        when(users.lockByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)).thenReturn(java.util.List.of(new User()));
        assertThatCode(() -> service.guardLosingAdmin(target)).doesNotThrowAnyException();
    }

    @Test
    void youCannotActOnYourself() {
        assertThatThrownBy(() -> service.guardNotSelf(caller, caller))
                .isInstanceOf(ApiException.class).hasMessageContaining("your own account");
    }

    @Test
    void bulkMoveCannotRemoveLastAdmin() {
        User a = User.builder().id("a").email("a@t.io").role(Role.ADMIN).build();
        User b = User.builder().id("b").email("b@t.io").role(Role.ADMIN).build();
        User actor = User.builder().id("actor").email("actor@t.io").role(Role.RECRUITER).build();
        when(users.findByEmailIgnoreCase("actor@t.io")).thenReturn(Optional.of(actor));
        when(users.findAllForUpdateByIdIn(java.util.Set.of("a", "b"))).thenReturn(java.util.List.of(a, b));
        when(users.lockByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)).thenReturn(java.util.List.of(a, b));

        assertThatThrownBy(() -> service.moveToRole(Role.RECRUITER, java.util.List.of("a", "b"), "actor@t.io"))
                .isInstanceOf(ApiException.class).hasMessage("At least one active admin must remain");
        org.mockito.Mockito.verify(users, org.mockito.Mockito.never()).saveAll(org.mockito.ArgumentMatchers.any());
        org.assertj.core.api.Assertions.assertThat(a.getRole()).isEqualTo(Role.ADMIN);

        when(users.lockByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)).thenReturn(java.util.List.of(a, b, new User()));
        org.assertj.core.api.Assertions.assertThat(service.moveToRole(Role.RECRUITER, java.util.List.of("a", "b"), "actor@t.io")).hasSize(2);
    }
}
