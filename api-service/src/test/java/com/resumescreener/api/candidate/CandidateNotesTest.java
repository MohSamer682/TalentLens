package com.resumescreener.api.candidate;

import com.resumescreener.api.audit.AuditService;
import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.session.SessionService;
import com.resumescreener.common.session.Candidate;
import com.resumescreener.common.session.CandidateNote;
import com.resumescreener.common.session.CandidateNoteRepository;
import com.resumescreener.common.session.CandidateRepository;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandidateNotesTest {

    private final SessionService sessions = mock(SessionService.class);
    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final CandidateNoteRepository notes = mock(CandidateNoteRepository.class);
    private final CandidateService service = new CandidateService(sessions, candidates, null, users, audit, notes, null);

    private final User author = User.builder().id("u1").email("a@example.com").fullName("Ana").role(Role.RECRUITER).build();
    private final User other = User.builder().id("u2").email("b@example.com").fullName("Ben").role(Role.RECRUITER).build();
    private final User admin = User.builder().id("u3").email("admin@example.com").fullName("Ada").role(Role.ADMIN).build();

    @BeforeEach
    void setUp() {
        for (User u : List.of(author, other, admin)) {
            when(users.findByEmailIgnoreCase(u.getEmail())).thenReturn(Optional.of(u));
        }
        when(candidates.findByIdAndSessionId("c1", "s1")).thenReturn(Optional.of(Candidate.builder().id("c1").build()));
        when(notes.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void addsTrimmedNoteAndAudits() {
        var dto = service.addNote("s1", "c1", "  Strong on Kafka  ", author.getEmail());

        assertEquals("Strong on Kafka", dto.content());
        assertTrue(dto.deletable());
        verify(audit).record(eq("CANDIDATE_NOTE_ADD"), anyString(), any(), eq("SUCCESS"), anyString());
    }

    @Test
    void outsiderCannotReadOrAdd() {
        when(sessions.getSession("s1", other.getEmail())).thenThrow(new AccessDeniedException("no"));

        assertThrows(AccessDeniedException.class, () -> service.listNotes("s1", "c1", other.getEmail()));
        assertThrows(AccessDeniedException.class, () -> service.addNote("s1", "c1", "hi", other.getEmail()));
        verify(notes, never()).save(any());
    }

    @Test
    void candidateFromAnotherSessionIs404() {
        assertThrows(ApiException.class, () -> service.listNotes("s1", "c-elsewhere", author.getEmail()));
        assertThrows(ApiException.class, () -> service.addNote("s1", "c-elsewhere", "hi", author.getEmail()));
        verify(notes, never()).findByCandidateIdOrderByCreatedAtDesc(anyString());
    }

    @Test
    void deleteChecksTheNoteBelongsToTheCandidate() {
        when(notes.findById("n1")).thenReturn(Optional.of(note("c2", author)));

        assertThrows(ApiException.class, () -> service.deleteNote("s1", "c1", "n1", author.getEmail()));
        verify(notes, never()).delete(any());
    }

    @Test
    void onlyAuthorOrAdminDeletes() {
        CandidateNote n = note("c1", author);
        when(notes.findById("n1")).thenReturn(Optional.of(n));

        assertThrows(ApiException.class, () -> service.deleteNote("s1", "c1", "n1", other.getEmail()));
        service.deleteNote("s1", "c1", "n1", admin.getEmail());
        service.deleteNote("s1", "c1", "n1", author.getEmail());
        verify(notes, org.mockito.Mockito.times(2)).delete(n);
    }

    private static CandidateNote note(String candidateId, User by) {
        return CandidateNote.builder().id("n1").candidateId(candidateId).author(by).content("x").build();
    }
}
