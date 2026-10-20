package com.resumescreener.api.pipeline;

import com.resumescreener.api.candidate.dto.PipelineCandidateDto;
import com.resumescreener.common.session.*;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class PipelineQueryServiceTest {

    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final CandidateScoreRepository scores = mock(CandidateScoreRepository.class);
    private final CandidateInterviewRepository interviews = mock(CandidateInterviewRepository.class);
    private final CandidateOfferRepository offers = mock(CandidateOfferRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PipelineSupport support = mock(PipelineSupport.class);
    private final PipelineQueryService service = new PipelineQueryService(candidates, scores, interviews, offers,
            mock(ScreeningSessionRepository.class), users, null, support);

    @Test
    void boardLoadsOffersScoresAndInterviewsInOneQueryEach() {
        when(support.getUser("chief@x.com")).thenReturn(
                User.builder().id("h1").email("chief@x.com").fullName("Chief").role(Role.HR_CHIEF).build());
        ScreeningSession session = ScreeningSession.builder().id("s1").title("Backend").build();
        List<Candidate> rows = List.of(candidate("c1", session), candidate("c2", session), candidate("c3", session));
        when(candidates.findByReviewStatusNot(ReviewStatus.NONE)).thenReturn(rows);
        CandidateScore score = CandidateScore.builder().candidate(rows.get(0)).compositeScore(88.0).build();
        when(scores.findByCandidateIdIn(anyCollection())).thenReturn(List.of(score));
        when(offers.findByCandidateIdIn(anyCollection())).thenReturn(List.of());
        when(interviews.findByCandidateIdInAndStatusOrderByScheduledStartAsc(anyCollection(), any())).thenReturn(List.of());

        List<PipelineCandidateDto> board = service.getPipeline(false, "chief@x.com");

        assertEquals(3, board.size());
        assertEquals(88.0, board.stream().filter(d -> d.id().equals("c1")).findFirst().orElseThrow().score());
        verify(offers, times(1)).findByCandidateIdIn(anyCollection());
        verify(scores, times(1)).findByCandidateIdIn(anyCollection());
        verify(offers, never()).findByCandidateId(any());
        verify(scores, never()).findByCandidateId(any());
    }

    private static Candidate candidate(String id, ScreeningSession session) {
        return Candidate.builder().id(id).session(session).name(id).status(CandidateStatus.SCORED)
                .reviewStatus(ReviewStatus.INTERVIEW_SCHEDULED).reviewedAt(Instant.now()).build();
    }
}
