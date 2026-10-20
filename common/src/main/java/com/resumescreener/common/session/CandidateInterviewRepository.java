package com.resumescreener.common.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface CandidateInterviewRepository extends JpaRepository<CandidateInterview, String> {

    @Query("SELECT i FROM CandidateInterview i JOIN FETCH i.interviewer WHERE i.candidateId = :candidateId ORDER BY i.scheduledStart ASC")
    List<CandidateInterview> findByCandidateIdOrderByScheduledStartAsc(@Param("candidateId") String candidateId);

    @Query("SELECT i FROM CandidateInterview i JOIN FETCH i.interviewer WHERE i.interviewer.id = :interviewerId ORDER BY i.scheduledStart ASC")
    List<CandidateInterview> findByInterviewerIdOrderByScheduledStartAsc(@Param("interviewerId") String interviewerId);

    @Query("SELECT i.candidateId FROM CandidateInterview i WHERE i.id = :id")
    java.util.Optional<String> findCandidateIdById(@Param("id") String id);

    List<CandidateInterview> findByCandidateIdAndStatus(String candidateId, InterviewStatus status);
    List<CandidateInterview> findByCandidateIdAndStatusOrderByScheduledStartAsc(String candidateId, InterviewStatus status);

    List<CandidateInterview> findByCandidateIdInAndStatusOrderByScheduledStartAsc(java.util.Collection<String> candidateIds, InterviewStatus status);
    long countByStatus(InterviewStatus status);

    long countByFeedbackSubmittedAtBetween(Instant from, Instant to);

    List<CandidateInterview> findByScheduledStartBetween(Instant from, Instant to);
}
