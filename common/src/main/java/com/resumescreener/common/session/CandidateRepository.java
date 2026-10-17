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
    long countBySessionIdAndReviewStatus(String sessionId, ReviewStatus reviewStatus);

    /** Shortlisted plus every later stage: a candidate who moves on is still on the shortlist */
    long countBySessionIdAndReviewStatusNot(String sessionId, ReviewStatus reviewStatus);
    List<Candidate> findByIdInAndSessionId(Collection<String> ids, String sessionId);
    java.util.Optional<Candidate> findByIdAndSessionId(String id, String sessionId);

    void deleteBySessionId(String sessionId);

    boolean existsBySessionIdAndContentHash(String sessionId, String contentHash);
    boolean existsBySessionIdAndStatus(String sessionId, CandidateStatus status);
    List<Candidate> findBySessionIdAndStatusIn(String sessionId, Collection<CandidateStatus> statuses);
    Page<Candidate> findBySessionIdOrderByCreatedAtDesc(String sessionId, Pageable pageable);
    Page<Candidate> findBySessionIdAndStatusOrderByCreatedAtDesc(String sessionId, CandidateStatus status, Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"session"})
    List<Candidate> findByReviewStatusNot(ReviewStatus reviewStatus);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"session"})
    List<Candidate> findBySessionIdInAndReviewStatusNot(Collection<String> sessionIds, ReviewStatus reviewStatus);
}