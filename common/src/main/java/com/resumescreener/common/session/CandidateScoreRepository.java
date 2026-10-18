package com.resumescreener.common.session;

import com.resumescreener.common.session.CandidateScore;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface CandidateScoreRepository extends JpaRepository<CandidateScore, String> {
    // Candidate is LAZY on CandidateScore and open-in-view is off, so callers that read candidate
    // fields outside a transaction need it fetched with the score
    @EntityGraph(attributePaths = "candidate")
    List<CandidateScore> findByCandidateId(String candidateId);
    @EntityGraph(attributePaths = "candidate")
    List<CandidateScore> findByCandidateSessionIdOrderByRankAsc(String sessionId);
    @EntityGraph(attributePaths = "candidate")
    Page<CandidateScore> findByCandidateSessionIdOrderByRankAsc(String sessionId, Pageable pageable);
    @EntityGraph(attributePaths = "candidate")
    Page<CandidateScore> findByCandidateSessionIdAndCandidateReviewStatusOrderByRankAsc(
            String sessionId, ReviewStatus reviewStatus, Pageable pageable);
    /** Everyone past the screening: shortlisted and every later stage */
    @EntityGraph(attributePaths = "candidate")
    Page<CandidateScore> findByCandidateSessionIdAndCandidateReviewStatusNotOrderByRankAsc(
            String sessionId, ReviewStatus excluded, Pageable pageable);
    @EntityGraph(attributePaths = "candidate")
    List<CandidateScore> findTop200ByCandidateSessionIdAndRankNotNullOrderByRankAsc(String sessionId);
}