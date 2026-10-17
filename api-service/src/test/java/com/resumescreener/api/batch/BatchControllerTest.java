package com.resumescreener.api.batch;

import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.common.ApiResponse;
import com.resumescreener.api.session.SessionService;
import com.resumescreener.common.session.CandidateResultDto;
import com.resumescreener.common.session.Candidate;
import com.resumescreener.common.session.CandidateScore;
import com.resumescreener.common.session.ScreeningSession;
import com.resumescreener.common.session.CandidateRepository;
import com.resumescreener.common.session.CandidateScoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchControllerTest {

    @Test
    void getCandidatesReturnsPersistedScoreRows() {
        Candidate candidate = Candidate.builder()
                .id("candidate-1")
                .name("Jane Candidate")
                .email("jane@example.com")
                .build();
        CandidateScore score = CandidateScore.builder()
                .id("score-1")
                .candidate(candidate)
                .rank(2)
                .technicalSkills(91.0)
                .experience(82.0)
                .education(73.0)
                .softSkills(64.0)
                .cultureFit(55.0)
                .compositeScore(78.25)
                .build();
        BatchController controller = new BatchController(
                null,
                new AuthorizingSessionService(),
                candidateRepositoryThatMustNotServeScores(),
                candidateScoreRepositoryReturning(score),
                null,
                null
        );

        ResponseEntity<ApiResponse<Page<CandidateResultDto>>> response = controller.getCandidates(
                "session-1",
                null,
                PageRequest.of(0, 20),
                new TestingAuthenticationToken("recruiter@example.com", null)
        );

        ApiResponse<Page<CandidateResultDto>> envelope = response.getBody();
        assertTrue(envelope.isSuccess());
        assertEquals("Candidates loaded", envelope.getMessage());

        CandidateResultDto dto = envelope.getData().getContent().get(0);
        assertEquals("candidate-1", dto.getId());
        assertEquals(2, dto.getRank());
        assertEquals("Jane Candidate", dto.getName());
        assertEquals("jane@example.com", dto.getEmail());
        assertEquals(91.0, dto.getTechnicalSkills());
        assertEquals(82.0, dto.getExperience());
        assertEquals(73.0, dto.getEducation());
        assertEquals(64.0, dto.getSoftSkills());
        assertEquals(55.0, dto.getCultureFit());
        assertEquals(78.25, dto.getCompositeScore());
    }

    @Test
    void candidateDetailRejectsCandidateFromAnotherSession() {
        Candidate foreign = Candidate.builder()
                .id("candidate-9")
                .session(ScreeningSession.builder().id("someone-elses-session").build())
                .build();
        CandidateScore score = CandidateScore.builder().id("score-9").candidate(foreign).build();
        CandidateScoreRepository scores = (CandidateScoreRepository) Proxy.newProxyInstance(
                CandidateScoreRepository.class.getClassLoader(),
                new Class<?>[]{CandidateScoreRepository.class},
                (proxy, method, args) -> "findByCandidateId".equals(method.getName()) ? List.of(score) : null);
        BatchController controller = new BatchController(
                null, new AuthorizingSessionService(), null, scores, null, null);

        assertThrows(ApiException.class, () -> controller.getCandidateDetail(
                "session-1", "candidate-9", new TestingAuthenticationToken("recruiter@example.com", null)));
    }

    private static CandidateRepository candidateRepositoryThatMustNotServeScores() {
        return (CandidateRepository) Proxy.newProxyInstance(
                CandidateRepository.class.getClassLoader(),
                new Class<?>[]{CandidateRepository.class},
                (proxy, method, args) -> {
                    if ("findBySessionId".equals(method.getName())) {
                        throw new AssertionError("Candidate list endpoint must read CandidateScore rows");
                    }
                    return null;
                }
        );
    }

    private static CandidateScoreRepository candidateScoreRepositoryReturning(CandidateScore score) {
        return (CandidateScoreRepository) Proxy.newProxyInstance(
                CandidateScoreRepository.class.getClassLoader(),
                new Class<?>[]{CandidateScoreRepository.class},
                (proxy, method, args) -> {
                    if ("findByCandidateSessionIdOrderByRankAsc".equals(method.getName())) {
                        return new PageImpl<>(List.of(score), (PageRequest) args[1], 1);
                    }
                    return null;
                }
        );
    }

    private static class AuthorizingSessionService extends SessionService {
        private AuthorizingSessionService() {
            super(null, null, null, null, null);
        }

        @Override
        public ScreeningSession getSession(String id, String userEmail) {
            return ScreeningSession.builder().id(id).build();
        }

        @Override
        public ScreeningSession getOwnedSession(String id, String userEmail) {
            return getSession(id, userEmail);
        }
    }
}
