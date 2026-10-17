package com.resumescreener.api.session;

import com.resumescreener.common.session.ScreeningSession;
import com.resumescreener.common.session.ScreeningSessionRepository;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Viewing (SESSION_VIEW_ALL) and changing (owner or admin) another recruiter's session are separate rights. */
class SessionAccessTest {

    private final ScreeningSessionRepository sessions = mock(ScreeningSessionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SessionService service = new SessionService(sessions, null, null, users, null);

    private final User owner = User.builder().id("owner").role(Role.RECRUITER).build();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void viewAllLetsHrChiefOpenOthersSession() {
        signIn("hr@example.com", Role.HR_CHIEF, "SESSION_VIEW_ALL");

        assertEquals("s1", service.getSession("s1", "hr@example.com").getId());
    }

    @Test
    void viewAllDoesNotLetHrChiefChangeIt() {
        signIn("hr@example.com", Role.HR_CHIEF, "SESSION_VIEW_ALL", "SESSION_RUN");

        assertThrows(AccessDeniedException.class, () -> service.getOwnedSession("s1", "hr@example.com"));
    }

    @Test
    void withoutViewAllOthersSessionIsHidden() {
        signIn("r2@example.com", Role.RECRUITER, "SESSION_RUN");

        assertThrows(AccessDeniedException.class, () -> service.getSession("s1", "r2@example.com"));
    }

    @Test
    void adminCanStillChangeAnySession() {
        signIn("admin@example.com", Role.ADMIN, "SESSION_VIEW_ALL");

        assertEquals("s1", service.getOwnedSession("s1", "admin@example.com").getId());
    }

    @Test
    void listingEveryoneNeedsViewAll() {
        signIn("r2@example.com", Role.RECRUITER, "SESSION_RUN");

        assertThrows(AccessDeniedException.class, () -> service.getSessions("r2@example.com", true, PageRequest.of(0, 20)));
    }

    private void signIn(String email, Role role, String... authorities) {
        when(users.findByEmailIgnoreCase(email)).thenReturn(Optional.of(User.builder().id(email).email(email).role(role).build()));
        when(sessions.findWithWeightsById("s1")).thenReturn(Optional.of(ScreeningSession.builder().id("s1").user(owner).build()));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(email, null, authorities));
    }
}
