package com.resumescreener.api.candidate;

import com.resumescreener.api.audit.AuditService;
import com.resumescreener.api.candidate.dto.BulkReviewRequest;
import com.resumescreener.api.candidate.dto.ReviewResult;
import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.session.SessionService;
import com.resumescreener.common.session.Candidate;
import com.resumescreener.common.session.CandidateRepository;
import com.resumescreener.common.session.CandidateScore;
import com.resumescreener.common.session.CandidateScoreRepository;
import com.resumescreener.common.session.CandidateStatus;
import com.resumescreener.common.session.ReviewStatus;
import com.resumescreener.common.session.ScreeningSession;
import com.resumescreener.common.session.SessionStatus;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandidateReviewTest {

    private static final String EMAIL = "recruiter@example.com";

    private final SessionService sessions = mock(SessionService.class);
    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final CandidateScoreRepository scores = mock(CandidateScoreRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final com.resumescreener.common.session.CandidateNoteRepository notes = mock(com.resumescreener.common.session.CandidateNoteRepository.class);
    private final com.resumescreener.common.session.CandidateStageEventRepository stageEvents = mock(com.resumescreener.common.session.CandidateStageEventRepository.class);
    private final CandidateService service = new CandidateService(sessions, candidates, scores, users, audit, notes, stageEvents);
    private final ScreeningSession session = ScreeningSession.builder().id("s1").status(SessionStatus.DONE).build();

    @BeforeEach
    void setUp() {
        when(sessions.getOwnedSession("s1", EMAIL)).thenReturn(session);
        when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(User.builder().id("u1").email(EMAIL).build()));
    }

    @Test
    void shortlistsOneScoredCandidate() {
        Candidate c = scored("c1");
        when(candidates.findByIdInAndSessionId(List.of("c1"), "s1")).thenReturn(List.of(c));
        when(candidates.countBySessionIdAndReviewStatusNot("s1", ReviewStatus.NONE)).thenReturn(1L);

        ReviewResult result = service.review("s1", List.of("c1"), ReviewStatus.SHORTLISTED, EMAIL);

        assertEquals(new ReviewResult(1, 1), result);
        assertEquals("u1", c.getReviewedBy());
        assertNotNull(c.getReviewedAt());
        verify(audit).record("CANDIDATE_SHORTLIST", "candidate", "c1", "success");
    }

    @Test
    void refusesUnscoredCandidate() {
        Candidate c = scored("c1");
        c.setStatus(CandidateStatus.FAILED);
        when(candidates.findByIdInAndSessionId(List.of("c1"), "s1")).thenReturn(List.of(c));

        ApiException e = assertThrows(ApiException.class,
                () -> service.review("s1", List.of("c1"), ReviewStatus.SHORTLISTED, EMAIL));
        assertEquals("Only scored resumes can be shortlisted", e.getMessage());
        verify(candidates, never()).saveAll(any());
    }

    @Test
    void refusesWhileSessionRunning() {
        session.setStatus(SessionStatus.RUNNING);

        ApiException e = assertThrows(ApiException.class,
                () -> service.review("s1", List.of("c1"), ReviewStatus.SHORTLISTED, EMAIL));
        assertEquals("Wait for scoring to finish before reviewing", e.getMessage());
    }

    @Test
    void bulkRejectsForeignIdsAndSavesNothing() {
        when(candidates.findByIdInAndSessionId(List.of("c1", "foreign"), "s1")).thenReturn(List.of(scored("c1")));

        assertThrows(ApiException.class,
                () -> service.review("s1", List.of("c1", "foreign"), ReviewStatus.SHORTLISTED, EMAIL));
        verify(candidates, never()).saveAll(any());
        verify(audit, never()).record(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void bulkRejectsMoreThan200Ids() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        List<String> ids = IntStream.range(0, 201).mapToObj(i -> "c" + i).toList();

        assertFalse(validator.validate(new BulkReviewRequest(ids, ReviewStatus.SHORTLISTED)).isEmpty());
        assertFalse(validator.validate(new BulkReviewRequest(Collections.emptyList(), ReviewStatus.SHORTLISTED)).isEmpty());
    }

    @Test
    void topNCapsAtScoredCount() {
        List<CandidateScore> ranked = List.of(rankedScore("c1"), rankedScore("c2"), rankedScore("c3"));
        when(scores.findTop200ByCandidateSessionIdAndRankNotNullOrderByRankAsc("s1")).thenReturn(ranked);
        when(candidates.countBySessionIdAndReviewStatusNot("s1", ReviewStatus.NONE)).thenReturn(3L);

        assertEquals(new ReviewResult(3, 3), service.shortlistTop("s1", 5, EMAIL));
    }

    @Test
    void shortlistingNeverDragsACandidateBackFromALaterStage() {
        Candidate contacted = scored("c1");
        contacted.setReviewStatus(ReviewStatus.CONTACTED);
        Candidate fresh = scored("c2");
        when(candidates.findByIdInAndSessionId(anyCollection(), eq("s1"))).thenReturn(List.of(contacted, fresh));

        ReviewResult result = service.review("s1", List.of("c1", "c2"), ReviewStatus.SHORTLISTED, EMAIL);

        assertEquals(1, result.changed());
        assertEquals(ReviewStatus.CONTACTED, contacted.getReviewStatus());
        assertEquals(ReviewStatus.SHORTLISTED, fresh.getReviewStatus());
    }

    @Test
    void unshortlistingLeavesCandidatesAlreadyInThePipeline() {
        Candidate interviewing = scored("c1");
        interviewing.setReviewStatus(ReviewStatus.INTERVIEW_SCHEDULED);
        interviewing.setReviewedBy("u9");
        when(candidates.findByIdInAndSessionId(anyCollection(), eq("s1"))).thenReturn(List.of(interviewing));

        assertEquals(0, service.review("s1", List.of("c1"), ReviewStatus.NONE, EMAIL).changed());
        assertEquals(ReviewStatus.INTERVIEW_SCHEDULED, interviewing.getReviewStatus());
        assertEquals("u9", interviewing.getReviewedBy());
    }

    @Test
    void settingNoneClearsReviewer() {
        Candidate c = scored("c1");
        c.setReviewStatus(ReviewStatus.SHORTLISTED);
        c.setReviewedBy("u9");
        when(candidates.findByIdInAndSessionId(anyCollection(), eq("s1"))).thenReturn(List.of(c));

        ReviewResult result = service.review("s1", List.of("c1"), ReviewStatus.NONE, EMAIL);

        assertEquals(1, result.changed());
        assertNull(c.getReviewedBy());
        assertNull(c.getReviewedAt());
        verify(audit).record("CANDIDATE_UNSHORTLIST", "candidate", "c1", "success");
    }

    @Test
    void unchangedRowsAreNotCountedOrAudited() {
        Candidate c = scored("c1");
        c.setReviewStatus(ReviewStatus.SHORTLISTED);
        when(candidates.findByIdInAndSessionId(anyCollection(), eq("s1"))).thenReturn(List.of(c));

        assertEquals(0, service.review("s1", List.of("c1"), ReviewStatus.SHORTLISTED, EMAIL).changed());
        verify(audit, never()).record(anyString(), anyString(), anyString(), anyString());
    }

    private static Candidate scored(String id) {
        return Candidate.builder().id(id).status(CandidateStatus.SCORED).build();
    }

    private static CandidateScore rankedScore(String id) {
        return CandidateScore.builder().candidate(scored(id)).rank(1).build();
    }
}
