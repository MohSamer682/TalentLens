package com.resumescreener.api.pipeline;

import com.resumescreener.api.audit.AuditService;
import com.resumescreener.api.candidate.dto.*;
import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.session.SessionService;
import com.resumescreener.common.session.*;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** Candidate stage workflow up to the offer: contact, interview plan, scheduling and feedback. */
@Service
@RequiredArgsConstructor
public class PipelineService {

    private final CandidateRepository candidateRepository;
    private final CandidateInterviewRepository interviewRepository;
    private final CandidateStageEventRepository stageEventRepository;
    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final AuditService auditService;
    private final PipelineSupport support;

    @Transactional(readOnly = true)
    public List<CandidateStageEventDto> getTimeline(String sessionId, String candidateId, String email) {
        sessionService.getSession(sessionId, email);
        support.candidateInSession(sessionId, candidateId);
        return stageEventRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId)
                .stream()
                .map(CandidateStageEventDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CandidateInterviewDto> getInterviews(String sessionId, String candidateId, String email) {
        sessionService.getSession(sessionId, email);
        support.candidateInSession(sessionId, candidateId);
        return interviewRepository.findByCandidateIdOrderByScheduledStartAsc(candidateId)
                .stream()
                .map(CandidateInterviewDto::from)
                .toList();
    }

    @Transactional
    public void markContacted(String sessionId, String candidateId, String email) {
        sessionService.getOwnedSession(sessionId, email);
        Candidate candidate = support.lockCandidate(sessionId, candidateId);
        if (candidate.getReviewStatus() != ReviewStatus.SHORTLISTED) {
            throw ApiException.validation("Candidate cannot be marked as contacted", "Must be at SHORTLISTED stage");
        }
        User actor = support.getUser(email);
        candidate.setReviewStatus(ReviewStatus.CONTACTED);
        candidate.setReviewedAt(Instant.now());
        candidateRepository.save(candidate);

        support.recordEvent(candidateId, "CONTACTED", actor, "Contacted by " + actor.getFullName());
        auditService.record("CANDIDATE_CONTACT", "CANDIDATE", candidateId, "SUCCESS");
    }

    /** The interviews the recruiter chose for this candidate; empty when none was chosen yet. */
    @Transactional(readOnly = true)
    public List<InterviewKind> getInterviewPlan(String sessionId, String candidateId, String email) {
        sessionService.getSession(sessionId, email);
        Candidate candidate = candidateRepository.findByIdAndSessionId(candidateId, sessionId)
                .orElseThrow(() -> ApiException.notFound("Candidate not found", "No candidate with id " + candidateId));
        return new ArrayList<>(planOf(candidate));
    }

    @Transactional
    public List<InterviewKind> setInterviewPlan(String sessionId, String candidateId, PlanInterviewsRequest req, String email) {
        sessionService.getOwnedSession(sessionId, email);
        Candidate candidate = support.lockCandidate(sessionId, candidateId);
        ReviewStatus stage = candidate.getReviewStatus();
        if (stage != ReviewStatus.CONTACTED && stage != ReviewStatus.INTERVIEW_SCHEDULED && stage != ReviewStatus.INTERVIEWED) {
            throw ApiException.validation("Cannot plan interviews", "Candidate must be between CONTACTED and OFFER stages");
        }

        Set<InterviewKind> plan = EnumSet.copyOf(req.kinds());
        Set<InterviewKind> inUse = interviewRepository.findByCandidateIdOrderByScheduledStartAsc(candidateId).stream()
                .filter(i -> i.getStatus() != InterviewStatus.CANCELLED)
                .map(CandidateInterview::getKind)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(InterviewKind.class)));
        if (!plan.containsAll(inUse)) {
            throw ApiException.validation("Cannot remove an interview that is already scheduled",
                    "Cancel the scheduled " + inUse.stream().filter(k -> !plan.contains(k)).findFirst().orElseThrow() + " interview first");
        }

        candidate.setInterviewPlan(plan.stream().map(Enum::name).collect(Collectors.joining(",")));
        candidateRepository.save(candidate);

        User actor = support.getUser(email);
        support.recordEvent(candidateId, "INTERVIEW_PLAN", actor, "Interviews planned: " + candidate.getInterviewPlan().replace(',', ' ') + " by " + actor.getFullName());
        auditService.record("INTERVIEW_PLAN", "CANDIDATE", candidateId, "SUCCESS");
        settleInterviewed(candidate);
        return new ArrayList<>(plan);
    }

    @Transactional
    public CandidateInterviewDto scheduleInterview(String sessionId, String candidateId, ScheduleInterviewRequest req, String email) {
        sessionService.getOwnedSession(sessionId, email);
        Candidate candidate = support.lockCandidate(sessionId, candidateId);

        ReviewStatus stage = candidate.getReviewStatus();
        if (stage != ReviewStatus.CONTACTED && stage != ReviewStatus.INTERVIEW_SCHEDULED && stage != ReviewStatus.INTERVIEWED) {
            throw ApiException.validation("Cannot schedule interview", "Candidate must be between CONTACTED and OFFER stages");
        }
        Set<InterviewKind> plan = planOf(candidate);
        if (!plan.isEmpty() && !plan.contains(req.kind())) {
            throw ApiException.validation("Interview is not in the plan", "Add " + req.kind() + " to this candidate's interviews first");
        }

        User interviewer = userRepository.findById(req.interviewerId())
                .orElseThrow(() -> ApiException.notFound("Interviewer not found", "No user with id " + req.interviewerId()));
        requireInterviewerFor(req.kind(), interviewer);
        String location = resolveLocation(req.placeType(), req.location());

        User creator = support.getUser(email);
        CandidateInterview interview = CandidateInterview.builder()
                .candidateId(candidateId)
                .kind(req.kind())
                .interviewer(interviewer)
                .scheduledStart(req.scheduledStart())
                .durationMinutes(req.resolvedDuration())
                .placeType(req.placeType())
                .location(location)
                .noteToInterviewer(req.noteToInterviewer())
                .status(InterviewStatus.SCHEDULED)
                .createdBy(creator)
                .build();

        interview = interviewRepository.save(interview);

        if (stage == ReviewStatus.CONTACTED || stage == ReviewStatus.INTERVIEWED) {
            candidate.setReviewStatus(ReviewStatus.INTERVIEW_SCHEDULED);
            candidate.setReviewedAt(Instant.now());
            candidateRepository.save(candidate);
        }

        support.recordEvent(candidateId, "INTERVIEW_SCHEDULED", creator,
                req.kind() + " interview scheduled with " + interviewer.getFullName() + " by " + creator.getFullName());
        auditService.record("INTERVIEW_SCHEDULE", "CANDIDATE_INTERVIEW", interview.getId(), "SUCCESS");

        return CandidateInterviewDto.from(interview);
    }

    @Transactional
    public CandidateInterviewDto rescheduleInterview(String sessionId, String candidateId, String interviewId, RescheduleInterviewRequest req, String email) {
        sessionService.getOwnedSession(sessionId, email);
        Candidate candidate = support.lockCandidate(sessionId, candidateId);
        CandidateInterview interview = interviewRepository.findById(interviewId)
                .filter(i -> i.getCandidateId().equals(candidateId))
                .orElseThrow(() -> ApiException.notFound("Interview not found", "No interview with id " + interviewId));

        if (interview.getStatus() != InterviewStatus.SCHEDULED) {
            throw ApiException.validation("Cannot reschedule interview", "Only scheduled interviews can be rescheduled");
        }

        interview.setScheduledStart(req.scheduledStart());
        if (req.durationMinutes() != null) {
            interview.setDurationMinutes(req.durationMinutes());
        }
        if (req.placeType() != null) {
            interview.setPlaceType(req.placeType());
            interview.setLocation(resolveLocation(req.placeType(), req.location()));
        } else if (req.location() != null) {
            interview.setLocation(resolveLocation(interview.getPlaceType(), req.location()));
        }
        interview.setUpdatedAt(Instant.now());
        interview = interviewRepository.save(interview);

        User actor = support.getUser(email);
        support.recordEvent(candidateId, "INTERVIEW_RESCHEDULED", actor,
                interview.getKind() + " interview rescheduled by " + actor.getFullName());
        auditService.record("INTERVIEW_RESCHEDULE", "CANDIDATE_INTERVIEW", interviewId, "SUCCESS");

        return CandidateInterviewDto.from(interview);
    }

    @Transactional
    public void cancelInterview(String sessionId, String candidateId, String interviewId, String email) {
        sessionService.getOwnedSession(sessionId, email);
        Candidate candidate = support.lockCandidate(sessionId, candidateId);
        CandidateInterview interview = interviewRepository.findById(interviewId)
                .filter(i -> i.getCandidateId().equals(candidateId))
                .orElseThrow(() -> ApiException.notFound("Interview not found", "No interview with id " + interviewId));

        if (interview.getStatus() != InterviewStatus.SCHEDULED) {
            throw ApiException.validation("Cannot cancel interview", "Only scheduled interviews can be cancelled");
        }

        interview.setStatus(InterviewStatus.CANCELLED);
        interview.setUpdatedAt(Instant.now());
        interviewRepository.save(interview);

        User actor = support.getUser(email);
        support.recordEvent(candidateId, "INTERVIEW_CANCELLED", actor,
                interview.getKind() + " interview cancelled by " + actor.getFullName());
        auditService.record("INTERVIEW_CANCEL", "CANDIDATE_INTERVIEW", interviewId, "SUCCESS");

        settleInterviewed(candidate);
    }

    @Transactional
    public CandidateInterviewDto submitFeedback(String interviewId, SubmitFeedbackRequest req, String email) {
        CandidateInterview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> ApiException.notFound("Interview not found", "No interview with id " + interviewId));

        User interviewer = support.getUser(email);
        if (interview.getInterviewer() == null || !interview.getInterviewer().getId().equals(interviewer.getId())) {
            throw ApiException.forbidden("Cannot submit feedback", "Only the assigned interviewer can submit feedback");
        }
        if (interview.getStatus() == InterviewStatus.COMPLETED) {
            throw ApiException.validation("Feedback was already submitted", "Feedback cannot be changed after submission");
        }
        if (interview.getStatus() == InterviewStatus.CANCELLED) {
            throw ApiException.validation("This interview was cancelled", "Cancelled interviews take no feedback");
        }
        if (interview.getScheduledStart().isAfter(Instant.now())) {
            throw ApiException.validation("You can give feedback once the interview has started", "Feedback can only be submitted once the interview has started");
        }

        interview.setRecommendation(req.recommendation());
        interview.setRating(req.rating());
        interview.setFeedbackAssessment(req.assessment().trim());
        interview.setFeedbackSubmittedAt(Instant.now());
        interview.setStatus(InterviewStatus.COMPLETED);
        interview.setUpdatedAt(Instant.now());
        interview = interviewRepository.saveAndFlush(interview);

        support.recordEvent(interview.getCandidateId(), "FEEDBACK_SUBMITTED", interviewer,
                "Feedback submitted by " + interviewer.getFullName() + ": " + req.recommendation());
        auditService.record("INTERVIEW_FEEDBACK_SUBMIT", "CANDIDATE_INTERVIEW", interviewId, "SUCCESS");

        candidateRepository.findForUpdateById(interview.getCandidateId()).ifPresent(this::settleInterviewed);

        return CandidateInterviewDto.from(interview);
    }


    /** The kinds the recruiter chose, in enum order; empty for candidates planned before plans existed. */
    static Set<InterviewKind> planOf(Candidate candidate) {
        Set<InterviewKind> plan = EnumSet.noneOf(InterviewKind.class);
        if (candidate.getInterviewPlan() != null && !candidate.getInterviewPlan().isBlank()) {
            for (String name : candidate.getInterviewPlan().split(",")) {
                plan.add(InterviewKind.valueOf(name.trim()));
            }
        }
        return plan;
    }

    /**
     * HQ needs no detail, ONLINE needs an http(s) link, OTHER needs the place written out. The link is stored
     * as typed so it can be shown and clicked as is.
     */
    static String resolveLocation(InterviewPlace place, String location) {
        String text = location == null ? "" : location.trim();
        return switch (place) {
            case HQ -> null;
            case ONLINE -> {
                if (!isWebLink(text)) {
                    throw ApiException.validation("Meeting link is required", "Paste a link that starts with http:// or https://");
                }
                yield text;
            }
            case OTHER -> {
                if (text.isEmpty()) {
                    throw ApiException.validation("Place is required", "Write where the interview takes place");
                }
                yield text;
            }
        };
    }

    private static boolean isWebLink(String text) {
        try {
            URI uri = new URI(text);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static void requireInterviewerFor(InterviewKind kind, User interviewer) {
        Role role = interviewer.getRole();
        boolean ok = role == Role.ADMIN || switch (kind) {
            case TECHNICAL -> role == Role.TECHNICAL_INTERVIEWER;
            case HR -> role == Role.HR_INTERVIEWER;
            case MANAGEMENT -> role.isTopManagement();
        };
        if (!ok) {
            String who = switch (kind) {
                case TECHNICAL -> "Technical interviews can only be assigned to technical interviewers";
                case HR -> "HR interviews can only be assigned to HR interviewers";
                case MANAGEMENT -> "Management interviews can only be assigned to the CEO, chairman or a department director";
            };
            throw ApiException.validation("Invalid interviewer", who);
        }
    }



    /**
     * Moves INTERVIEW_SCHEDULED to INTERVIEWED once nothing is waiting and every planned interview has been
     * held. Without a plan (older candidates), one held interview is enough as before.
     */
    private void settleInterviewed(Candidate candidate) {
        if (candidate.getReviewStatus() != ReviewStatus.INTERVIEW_SCHEDULED) {
            return;
        }
        List<CandidateInterview> all = interviewRepository.findByCandidateIdOrderByScheduledStartAsc(candidate.getId());
        if (all.stream().anyMatch(i -> i.getStatus() == InterviewStatus.SCHEDULED)) {
            return;
        }
        Set<InterviewKind> held = all.stream().filter(i -> i.getStatus() == InterviewStatus.COMPLETED)
                .map(CandidateInterview::getKind)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(InterviewKind.class)));
        Set<InterviewKind> plan = planOf(candidate);
        boolean covered = plan.isEmpty() ? !held.isEmpty() : held.containsAll(plan);
        if (covered) {
            candidate.setReviewStatus(ReviewStatus.INTERVIEWED);
            candidate.setReviewedAt(Instant.now());
            candidateRepository.save(candidate);
            support.recordEvent(candidate.getId(), "INTERVIEWED", null, "All planned interviews completed");
        }
    }
}
