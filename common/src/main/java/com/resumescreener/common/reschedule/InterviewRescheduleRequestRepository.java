package com.resumescreener.common.reschedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InterviewRescheduleRequestRepository extends JpaRepository<InterviewRescheduleRequest, String> {

    Optional<InterviewRescheduleRequest> findByInterviewIdAndStatus(String interviewId, RescheduleStatus status);

    List<InterviewRescheduleRequest> findByInterviewIdInOrderByCreatedAtDesc(Collection<String> interviewIds);

    /** Which of these candidates have a request waiting; the pending partial index keeps it cheap. */
    @Query("select distinct i.candidateId from InterviewRescheduleRequest r, com.resumescreener.common.session.CandidateInterview i "
            + "where r.status = com.resumescreener.common.reschedule.RescheduleStatus.PENDING and i.id = r.interviewId and i.candidateId in :candidateIds")
    List<String> candidateIdsWithPendingRequest(Collection<String> candidateIds);
}
