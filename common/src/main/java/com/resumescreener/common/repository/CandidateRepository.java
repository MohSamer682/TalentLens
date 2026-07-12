package com.resumescreener.common.repository;

import com.resumescreener.common.entity.Candidate;
import com.resumescreener.common.enums.CandidateStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CandidateRepository extends JpaRepository<Candidate, String> {
    Page<Candidate> findBySessionId(String sessionId, Pageable pageable);
    Page<Candidate> findBySessionIdAndStatus(String sessionId, CandidateStatus status, Pageable pageable);
    List<Candidate> findBySessionId(String sessionId);
    long countBySessionId(String sessionId);
    long countBySessionIdAndStatus(String sessionId, CandidateStatus status);

    void deleteBySessionId(String sessionId);
}