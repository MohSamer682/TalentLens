package com.resumescreener.common.reschedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InterviewRescheduleRequestRepository extends JpaRepository<InterviewRescheduleRequest, String> {

    Optional<InterviewRescheduleRequest> findByInterviewIdAndStatus(String interviewId, RescheduleStatus status);

    List<InterviewRescheduleRequest> findByInterviewIdInOrderByCreatedAtDesc(Collection<String> interviewIds);

    /** Candidates with a request waiting; backs the pipeline badge through the partial pending index. */
    @Query("select distinct i.candidateId from InterviewRescheduleRequest r, com.resumescreener.common.session.CandidateInterview i "
            + "where r.status = com.resumescreener.common.reschedule.RescheduleStatus.PENDING and i.id = r.interviewId")
    List<String> candidatesWithPendingRequests();
}
