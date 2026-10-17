package com.resumescreener.common.session;

import com.resumescreener.common.session.Candidate;
import com.resumescreener.common.session.CandidateStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface CandidateRepository extends JpaRepository<Candidate, String> {
    Page<Candidate> findBySessionId(String sessionId, Pageable pageable);
    Page<Candidate> findBySessionIdAndStatus(String sessionId, CandidateStatus status, Pageable pageable);
    List<Candidate> findBySessionId(String sessionId);
    long countBySessionId(String sessionId);
    long countBySessionIdAndStatus(String sessionId, CandidateStatus status);

    void deleteBySessionId(String sessionId);

    boolean existsBySessionIdAndContentHash(String sessionId, String contentHash);
    boolean existsBySessionIdAndStatus(String sessionId, CandidateStatus status);
    List<Candidate> findBySessionIdAndStatusIn(String sessionId, Collection<CandidateStatus> statuses);
    Page<Candidate> findBySessionIdOrderByCreatedAtDesc(String sessionId, Pageable pageable);
    Page<Candidate> findBySessionIdAndStatusOrderByCreatedAtDesc(String sessionId, CandidateStatus status, Pageable pageable);
}