package com.resumescreener.common.reschedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InterviewRescheduleRequestRepository extends JpaRepository<InterviewRescheduleRequest, String> {

    Optional<InterviewRescheduleRequest> findByInterviewIdAndStatus(String interviewId, RescheduleStatus status);

    List<InterviewRescheduleRequest> findByInterviewIdInOrderByCreatedAtDesc(Collection<String> interviewIds);

    /** Which of these candidates still have a request HR can act on: pending, on an interview that hasn't started. */
    @Query("select distinct i.candidateId from InterviewRescheduleRequest r, com.resumescreener.common.session.CandidateInterview i "
            + "where r.status = com.resumescreener.common.reschedule.RescheduleStatus.PENDING and i.id = r.interviewId "
            + "and i.status = com.resumescreener.common.session.InterviewStatus.SCHEDULED and i.scheduledStart > :now "
            + "and i.candidateId in :candidateIds")
    List<String> candidateIdsWithPendingRequest(Collection<String> candidateIds, Instant now);

    /** The candidate a request belongs to, read without loading anything so the candidate lock comes first. */
    @Query("select i.candidateId from InterviewRescheduleRequest r, com.resumescreener.common.session.CandidateInterview i "
            + "where r.id = :requestId and i.id = r.interviewId")
    Optional<String> findCandidateIdByRequestId(String requestId);
}
