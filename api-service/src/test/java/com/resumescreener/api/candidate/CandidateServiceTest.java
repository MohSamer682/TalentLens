package com.resumescreener.api.candidate;

import com.resumescreener.api.candidate.dto.ResumeFileDto;
import com.resumescreener.api.session.SessionService;
import com.resumescreener.common.session.Candidate;
import com.resumescreener.common.session.CandidateRepository;
import com.resumescreener.common.session.CandidateStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CandidateServiceTest {

    private final SessionService sessions = mock(SessionService.class);
    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final com.resumescreener.common.session.CandidateNoteRepository notes = mock(com.resumescreener.common.session.CandidateNoteRepository.class);
    private final CandidateService service = new CandidateService(sessions, candidates, null, null, null, notes);
    private final PageRequest page = PageRequest.of(0, 50);

    @Test
    void listsAllResumesNewestFirst() {
        Candidate c = Candidate.builder().id("c1").name("jane").originalFileName("jane.pdf")
                .status(CandidateStatus.PENDING).build();
        when(candidates.findBySessionIdOrderByCreatedAtDesc("s1", page)).thenReturn(new PageImpl<>(List.of(c)));

        Page<ResumeFileDto> result = service.listResumes("s1", null, page, "r@example.com");

        assertEquals("jane.pdf", result.getContent().get(0).originalFileName());
        verify(sessions).getSession("s1", "r@example.com");
    }

    @Test
    void filtersByStatus() {
        when(candidates.findBySessionIdAndStatusOrderByCreatedAtDesc("s1", CandidateStatus.FAILED, page))
                .thenReturn(Page.empty());

        service.listResumes("s1", CandidateStatus.FAILED, page, "r@example.com");

        verify(candidates, never()).findBySessionIdOrderByCreatedAtDesc(anyString(), any());
    }

    @Test
    void otherUsersSessionIsForbidden() {
        when(sessions.getSession("s1", "other@example.com")).thenThrow(new AccessDeniedException("Not authorized"));

        assertThrows(AccessDeniedException.class, () -> service.listResumes("s1", null, page, "other@example.com"));
        verifyNoInteractions(candidates);
    }
}
