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
        when(users.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> service.guardLosingAdmin(target))
                .isInstanceOf(ApiException.class).hasMessage("At least one active admin must remain");
    }

    @Test
    void anotherActiveAdminLetsItThrough() {
        when(users.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)).thenReturn(2L);
        assertThatCode(() -> service.guardLosingAdmin(target)).doesNotThrowAnyException();
    }

    @Test
    void aHiddenAdminIsNotCountedAsTheLastOne() {
        target.hide();
        when(users.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)).thenReturn(1L);
        assertThatCode(() -> service.guardLosingAdmin(target)).doesNotThrowAnyException();
    }

    @Test
    void youCannotActOnYourself() {
        assertThatThrownBy(() -> service.guardNotSelf(caller, caller))
                .isInstanceOf(ApiException.class).hasMessageContaining("your own account");
    }
}
