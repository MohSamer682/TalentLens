package com.resumescreener.common.repository;

import com.resumescreener.common.entity.CandidateScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface CandidateScoreRepository extends JpaRepository<CandidateScore, String> {
    List<CandidateScore> findByCandidateId(String candidateId);
    List<CandidateScore> findByCandidateSessionIdOrderByRankAsc(String sessionId);
    Page<CandidateScore> findByCandidateSessionIdOrderByRankAsc(String sessionId, Pageable pageable);
}