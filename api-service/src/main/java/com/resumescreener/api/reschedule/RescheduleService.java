package com.resumescreener.api.reschedule;

import com.resumescreener.api.audit.AuditService;
import com.resumescreener.api.candidate.dto.RescheduleInterviewRequest;
import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.pipeline.PipelineService;
import com.resumescreener.api.reschedule.dto.AcceptRescheduleBody;
import com.resumescreener.api.reschedule.dto.DeclineRescheduleBody;
import com.resumescreener.api.reschedule.dto.RescheduleRequestBody;
import com.resumescreener.api.reschedule.dto.RescheduleView;
import com.resumescreener.api.session.SessionService;
import com.resumescreener.common.reschedule.InterviewRescheduleRequest;
import com.resumescreener.common.reschedule.InterviewRescheduleRequestRepository;
import com.resumescreener.common.reschedule.RescheduleStatus;
import com.resumescreener.common.session.CandidateInterview;
import com.resumescreener.common.session.CandidateInterviewRepository;
import com.resumescreener.common.session.CandidateRepository;
import com.resumescreener.common.session.InterviewStatus;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A candidate asks to move an interview; HR moves it to a time or declines with a reason. Accepting goes through
 * {@link PipelineService#rescheduleInterview}, so the usual ownership rules, timeline entry and audit apply, and
 * the request closes in the same transaction.
 */
@Service
@RequiredArgsConstructor
public class RescheduleService {

    static final Duration HORIZON = Duration.ofDays(60);
    static final String ANSWERED = "This request was already answered";

    private final InterviewRescheduleRequestRepository requests;
    private final CandidateInterviewRepository interviews;
    private final CandidateRepository candidates;
    private final UserRepository users;
    private final SessionService sessionService;
    private final PipelineService pipelineService;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public RescheduleView request(String email, String interviewId, RescheduleRequestBody body) {
        User me = user(email);
        // Lock before reading the interview, so a cancel that holds the lock is seen once it commits
        String candidateId = interviews.findCandidateIdById(interviewId).orElseThrow(RescheduleService::interviewNotFound);
        candidates.findForUpdateById(candidateId)
                .filter(c -> me.getId().equals(c.getAccountUserId()))
                .orElseThrow(RescheduleService::interviewNotFound);
        CandidateInterview interview = interviews.findById(interviewId).orElseThrow(RescheduleService::interviewNotFound);
        Instant now = clock.instant();
        if (interview.getStatus() != InterviewStatus.SCHEDULED) {
            throw ApiException.validation("Only a scheduled interview can be moved", "Interview is " + interview.getStatus());
        }
        if (!interview.getScheduledStart().isAfter(now)) {
            throw ApiException.validation("This interview has already started", "Requests close when the interview starts");
        }
        checkSlots(body.proposedStarts(), now);
        if (requests.findByInterviewIdAndStatus(interviewId, RescheduleStatus.PENDING).isPresent()) {
            throw ApiException.conflict("You already asked to move this interview. Your recruiter will reply here.",
                    "A pending request exists for interview " + interviewId);
        }
        InterviewRescheduleRequest saved = requests.saveAndFlush(
                InterviewRescheduleRequest.open(interviewId, me.getId(), body.reason().trim(), body.proposedStarts(), now));
        auditService.record("RESCHEDULE_REQUESTED", "RESCHEDULE_REQUEST", saved.getId(), "success");
        return RescheduleView.of(saved, me.getFullName());
    }

    @Transactional
    public RescheduleView withdraw(String email, String requestId) {
        User me = user(email);
        requests.findCandidateIdByRequestId(requestId)
                .flatMap(candidates::findForUpdateById)
                .filter(c -> me.getId().equals(c.getAccountUserId()))
                .orElseThrow(RescheduleService::requestNotFound);
        InterviewRescheduleRequest request = requests.findById(requestId)
                .filter(r -> r.getRequestedBy().equals(me.getId()))
                .orElseThrow(RescheduleService::requestNotFound);
        answer(() -> request.withdraw(clock.instant()));
        auditService.record("RESCHEDULE_WITHDRAWN", "RESCHEDULE_REQUEST", requestId, "success");
        return RescheduleView.of(requests.saveAndFlush(request), me.getFullName());
    }

    @Transactional(readOnly = true)
    public List<RescheduleView> listForCandidate(String sessionId, String candidateId, String email) {
        sessionService.getSession(sessionId, email);
        candidates.findByIdAndSessionId(candidateId, sessionId)
                .orElseThrow(() -> ApiException.notFound("Candidate not found", "No candidate " + candidateId + " in session " + sessionId));
        List<String> interviewIds = interviews.findByCandidateIdOrderByScheduledStartAsc(candidateId).stream().map(CandidateInterview::getId).toList();
        List<InterviewRescheduleRequest> found = requests.findByInterviewIdInOrderByCreatedAtDesc(interviewIds);
        Map<String, String> names = users.findAllById(new HashSet<>(found.stream().map(InterviewRescheduleRequest::getRequestedBy).toList()))
                .stream().collect(Collectors.toMap(User::getId, User::getFullName));
        return found.stream().map(r -> RescheduleView.of(r, names.get(r.getRequestedBy()))).toList();
    }

    @Transactional
    public RescheduleView accept(String sessionId, String candidateId, String requestId, AcceptRescheduleBody body, String email) {
        InterviewRescheduleRequest request = answerable(sessionId, candidateId, requestId, body.version(), email);
        if (!body.scheduledStart().isAfter(clock.instant())) {
            throw ApiException.validation("Pick a time in the future", "New start " + body.scheduledStart() + " has passed");
        }
        pipelineService.rescheduleInterview(sessionId, candidateId, request.getInterviewId(),
                new RescheduleInterviewRequest(body.scheduledStart(), null, null, null), email);
        answer(() -> request.accept(user(email).getId(), clock.instant()));
        auditService.record("RESCHEDULE_ACCEPTED", "RESCHEDULE_REQUEST", requestId, "success");
        return view(requests.saveAndFlush(request));
    }

    @Transactional
    public RescheduleView decline(String sessionId, String candidateId, String requestId, DeclineRescheduleBody body, String email) {
        InterviewRescheduleRequest request = answerable(sessionId, candidateId, requestId, body.version(), email);
        answer(() -> request.decline(user(email).getId(), body.note().trim(), clock.instant()));
        auditService.record("RESCHEDULE_DECLINED", "RESCHEDULE_REQUEST", requestId, "success");
        return view(requests.saveAndFlush(request));
    }

    /** Owner only, the request must belong to this candidate, and the caller must have seen its latest version. */
    private InterviewRescheduleRequest answerable(String sessionId, String candidateId, String requestId, long version, String email) {
        sessionService.getOwnedSession(sessionId, email);
        requests.findCandidateIdByRequestId(requestId)
                .filter(candidateId::equals)
                .flatMap(candidates::findForUpdateById)
                .filter(c -> sessionId.equals(c.getSession().getId()))
                .orElseThrow(RescheduleService::requestNotFound);
        InterviewRescheduleRequest request = requests.findById(requestId).orElseThrow(RescheduleService::requestNotFound);
        if (request.getStatus() != RescheduleStatus.PENDING) {
            throw ApiException.conflict(ANSWERED, "The request is " + request.getStatus());
        }
        if (request.getVersion() != version) {
            throw ApiException.conflict("This request changed since you opened it. Reload to see it.", "Stale version " + version);
        }
        return request;
    }

    private void checkSlots(List<Instant> slots, Instant now) {
        if (new HashSet<>(slots).size() != slots.size()) {
            throw ApiException.validation("Suggest different times", "Two proposed times are the same");
        }
        for (Instant slot : slots) {
            if (!slot.isAfter(now)) {
                throw ApiException.validation("Suggest times in the future", "Proposed time " + slot + " has passed");
            }
            if (slot.isAfter(now.plus(HORIZON))) {
                throw ApiException.validation("Suggest times within the next 60 days", "Proposed time " + slot + " is too far ahead");
            }
        }
    }

    private static void answer(Runnable transition) {
        try {
            transition.run();
        } catch (IllegalStateException e) {
            throw ApiException.conflict(ANSWERED, e.getMessage());
        }
    }

    private RescheduleView view(InterviewRescheduleRequest request) {
        return RescheduleView.of(request, users.findById(request.getRequestedBy()).map(User::getFullName).orElse(null));
    }

    private User user(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow(() -> ApiException.notFound("User not found", "No user for " + email));
    }

    private static ApiException interviewNotFound() {
        return ApiException.notFound("Interview not found", "No interview with this id on your account");
    }

    private static ApiException requestNotFound() {
        return ApiException.notFound("Request not found", "No reschedule request with this id");
    }
}
