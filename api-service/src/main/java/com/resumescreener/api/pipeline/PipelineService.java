package com.resumescreener.api.pipeline;

import com.resumescreener.api.audit.AuditService;
import com.resumescreener.api.candidate.dto.*;
import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.common.Authorities;
import com.resumescreener.api.session.SessionService;
import com.resumescreener.api.upload.MinioUploadService;
import com.resumescreener.common.session.*;
import com.resumescreener.common.user.Permission;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PipelineService {

    private final CandidateRepository candidateRepository;
    private final CandidateScoreRepository candidateScoreRepository;
    private final CandidateInterviewRepository interviewRepository;
    private final CandidateOfferRepository offerRepository;
    private final CandidateStageEventRepository stageEventRepository;
    private final SessionService sessionService;
    private final ScreeningSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final MinioUploadService uploadService;

    @Transactional(readOnly = true)
    public List<CandidateStageEventDto> getTimeline(String sessionId, String candidateId, String email) {
        sessionService.getSession(sessionId, email);
        candidateInSession(sessionId, candidateId);
        return stageEventRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId)
                .stream()
                .map(CandidateStageEventDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CandidateInterviewDto> getInterviews(String sessionId, String candidateId, String email) {
        sessionService.getSession(sessionId, email);
        candidateInSession(sessionId, candidateId);
        return interviewRepository.findByCandidateIdOrderByScheduledStartAsc(candidateId)
                .stream()
                .map(CandidateInterviewDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<CandidateOfferDto> getOffer(String sessionId, String candidateId, String email) {
        sessionService.getSession(sessionId, email);
        candidateInSession(sessionId, candidateId);
        return offerRepository.findByCandidateId(candidateId).map(CandidateOfferDto::from);
    }

    @Transactional
    public void markContacted(String sessionId, String candidateId, String email) {
        sessionService.getOwnedSession(sessionId, email);
        Candidate candidate = lockCandidate(sessionId, candidateId);
        requireStage(candidate, ReviewStatus.SHORTLISTED);
        User actor = getUser(email);
        candidate.setReviewStatus(ReviewStatus.CONTACTED);
        candidate.setReviewedAt(Instant.now());
        candidateRepository.save(candidate);

        recordEvent(candidateId, "CONTACTED", actor, "Contacted by " + actor.getFullName());
        auditService.record("CANDIDATE_CONTACT", "CANDIDATE", candidateId, "SUCCESS");
    }

    @Transactional
    public CandidateInterviewDto scheduleInterview(String sessionId, String candidateId, ScheduleInterviewRequest req, String email) {
        sessionService.getOwnedSession(sessionId, email);
        Candidate candidate = lockCandidate(sessionId, candidateId);
        requireStage(candidate, ReviewStatus.CONTACTED, ReviewStatus.INTERVIEW_SCHEDULED, ReviewStatus.INTERVIEWED);

        User interviewer = userRepository.findById(req.interviewerId())
                .orElseThrow(() -> ApiException.notFound("Interviewer not found", "No user with id " + req.interviewerId()));

        if (req.kind() == InterviewKind.TECHNICAL && interviewer.getRole() != Role.TECHNICAL_INTERVIEWER) {
            throw ApiException.validation("Choose a technical interviewer for a technical interview", "Interviewer role is " + interviewer.getRole());
        }
        if (req.kind() == InterviewKind.HR && interviewer.getRole() != Role.HR_INTERVIEWER) {
            throw ApiException.validation("Choose an HR interviewer for an HR interview", "Interviewer role is " + interviewer.getRole());
        }

        User creator = getUser(email);
        CandidateInterview interview = CandidateInterview.builder()
                .candidateId(candidateId)
                .kind(req.kind())
                .interviewer(interviewer)
                .scheduledStart(req.scheduledStart())
                .durationMinutes(req.resolvedDuration())
                .location(req.location())
                .noteToInterviewer(req.noteToInterviewer())
                .status(InterviewStatus.SCHEDULED)
                .createdBy(creator)
                .build();

        interview = interviewRepository.saveAndFlush(interview);

        recordEvent(candidateId, "INTERVIEW_SCHEDULED", creator,
                req.kind() + " interview scheduled with " + interviewer.getFullName() + " by " + creator.getFullName());
        recomputeInterviewStage(candidate, creator);
        auditService.record("INTERVIEW_SCHEDULE", "CANDIDATE_INTERVIEW", interview.getId(), "SUCCESS");

        return CandidateInterviewDto.from(interview);
    }

    @Transactional
    public CandidateInterviewDto rescheduleInterview(String sessionId, String candidateId, String interviewId, RescheduleInterviewRequest req, String email) {
        sessionService.getOwnedSession(sessionId, email);
        Candidate candidate = lockCandidate(sessionId, candidateId);
        CandidateInterview interview = interviewRepository.findById(interviewId)
                .filter(i -> i.getCandidateId().equals(candidateId))
                .orElseThrow(() -> ApiException.notFound("Interview not found", "No interview " + interviewId + " for candidate " + candidateId));
        if (interview.getStatus() != InterviewStatus.SCHEDULED) {
            throw ApiException.validation("Cannot reschedule interview", "Only scheduled interviews can be rescheduled");
        }

        interview.setScheduledStart(req.scheduledStart());
        if (req.durationMinutes() != null) {
            interview.setDurationMinutes(req.durationMinutes());
        }
        if (req.location() != null) {
            interview.setLocation(req.location());
        }
        interview.setUpdatedAt(Instant.now());
        interview = interviewRepository.save(interview);

        User actor = getUser(email);
        recordEvent(candidateId, "INTERVIEW_RESCHEDULED", actor,
                interview.getKind() + " interview rescheduled by " + actor.getFullName());
        auditService.record("INTERVIEW_RESCHEDULE", "CANDIDATE_INTERVIEW", interviewId, "SUCCESS");

        return CandidateInterviewDto.from(interview);
    }

    @Transactional
    public void cancelInterview(String sessionId, String candidateId, String interviewId, String email) {
        sessionService.getOwnedSession(sessionId, email);
        Candidate candidate = lockCandidate(sessionId, candidateId);
        CandidateInterview interview = interviewRepository.findById(interviewId)
                .filter(i -> i.getCandidateId().equals(candidateId))
                .orElseThrow(() -> ApiException.notFound("Interview not found", "No interview " + interviewId + " for candidate " + candidateId));
        if (interview.getStatus() != InterviewStatus.SCHEDULED) {
            throw ApiException.validation("Cannot cancel interview", "Only scheduled interviews can be cancelled");
        }

        interview.setStatus(InterviewStatus.CANCELLED);
        interview.setUpdatedAt(Instant.now());
        interviewRepository.save(interview);

        User actor = getUser(email);
        recordEvent(candidateId, "INTERVIEW_CANCELLED", actor,
                interview.getKind() + " interview cancelled by " + actor.getFullName());
        auditService.record("INTERVIEW_CANCEL", "CANDIDATE_INTERVIEW", interviewId, "SUCCESS");

        interviewRepository.flush();
        recomputeInterviewStage(candidate, actor);
    }

    @Transactional
    public CandidateInterviewDto submitFeedback(String interviewId, SubmitFeedbackRequest req, String email) {
        CandidateInterview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> ApiException.notFound("Interview not found", "No interview with id " + interviewId));

        User interviewer = getUser(email);
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
            throw ApiException.validation("You can give feedback once the interview has started", "Starts at " + interview.getScheduledStart());
        }
        Candidate candidate = candidateRepository.findForUpdateById(interview.getCandidateId())
                .orElseThrow(() -> ApiException.notFound("Candidate not found", "No candidate for interview " + interviewId));

        interview.setRecommendation(req.recommendation());
        interview.setRating(req.rating());
        interview.setFeedbackAssessment(req.assessment().trim());
        interview.setFeedbackSubmittedAt(Instant.now());
        interview.setStatus(InterviewStatus.COMPLETED);
        interview.setUpdatedAt(Instant.now());
        interview = interviewRepository.saveAndFlush(interview);

        recordEvent(interview.getCandidateId(), "FEEDBACK_SUBMITTED", interviewer,
                "Feedback submitted by " + interviewer.getFullName() + ": " + req.recommendation());
        auditService.record("INTERVIEW_FEEDBACK_SUBMIT", "CANDIDATE_INTERVIEW", interviewId, "SUCCESS");
        recomputeInterviewStage(candidate, interviewer);

        return CandidateInterviewDto.from(interview);
    }

    @Transactional
    public void moveToOffer(String sessionId, String candidateId, String email) {
        sessionService.getOwnedSession(sessionId, email);
        Candidate candidate = lockCandidate(sessionId, candidateId);
        requireStage(candidate, ReviewStatus.INTERVIEWED);

        User actor = getUser(email);
        candidate.setReviewStatus(ReviewStatus.OFFER);
        candidate.setReviewedAt(Instant.now());
        candidateRepository.save(candidate);

        recordEvent(candidateId, "OFFER", actor, "Moved to offer by " + actor.getFullName());
        auditService.record("CANDIDATE_MOVE_TO_OFFER", "CANDIDATE", candidateId, "SUCCESS");
    }

    @Transactional
    public CandidateOfferDto uploadOffer(String sessionId, String candidateId, MultipartFile file, String email) {
        sessionService.getOwnedSession(sessionId, email);
        Candidate candidate = lockCandidate(sessionId, candidateId);
        requireStage(candidate, ReviewStatus.OFFER);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.validation("Could not read file", e.getMessage());
        }

        if (bytes.length > 10L * 1024 * 1024) {
            throw ApiException.validation("Upload the offer as a PDF up to 10 MB", "File is " + bytes.length + " bytes");
        }
        if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            throw ApiException.validation("Upload the offer as a PDF up to 10 MB", "File does not start with %PDF-");
        }

        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "offer.pdf";
        String fileKey = uploadService.uploadOfferFile(sessionId, candidateId, fileName, bytes);

        User uploader = getUser(email);
        CandidateOffer offer = offerRepository.findByCandidateId(candidateId).orElse(null);
        if (offer == null) {
            offer = CandidateOffer.builder()
                    .candidateId(candidateId)
                    .fileKey(fileKey)
                    .fileName(fileName)
                    .sizeBytes(bytes.length)
                    .uploadedBy(uploader)
                    .build();
        } else {
            offer.setFileKey(fileKey);
            offer.setFileName(fileName);
            offer.setSizeBytes(bytes.length);
            offer.setUploadedBy(uploader);
            offer.setUploadedAt(Instant.now());
            offer.setApprovedBy(null);
            offer.setApprovedAt(null);
        }
        offer = offerRepository.save(offer);

        recordEvent(candidateId, "OFFER", uploader, "Offer uploaded by " + uploader.getFullName());
        auditService.record("OFFER_UPLOAD", "CANDIDATE_OFFER", offer.getId(), "SUCCESS");

        return CandidateOfferDto.from(offer);
    }

    @Transactional
    public CandidateOfferDto approveOffer(String sessionId, String candidateId, String email) {
        sessionService.getSession(sessionId, email);
        Candidate candidate = lockCandidate(sessionId, candidateId);
        requireStage(candidate, ReviewStatus.OFFER);
        CandidateOffer offer = offerRepository.findByCandidateId(candidateId)
                .orElseThrow(() -> ApiException.notFound("Offer not found", "No offer exists for this candidate"));

        User approver = getUser(email);
        if (offer.getUploadedBy() != null && offer.getUploadedBy().getId().equals(approver.getId())) {
            throw ApiException.validation("Someone other than the uploader must approve this offer", "Approver uploaded the offer");
        }
        if (offer.getApprovedAt() != null) {
            throw ApiException.validation("Offer already approved", "This offer has already been approved");
        }

        offer.setApprovedBy(approver);
        offer.setApprovedAt(Instant.now());
        offer = offerRepository.save(offer);

        candidate.setReviewStatus(ReviewStatus.OFFER_APPROVED);
        candidate.setReviewedAt(Instant.now());
        candidateRepository.save(candidate);

        recordEvent(candidateId, "OFFER_APPROVED", approver, "Offer approved by " + approver.getFullName());
        auditService.record("OFFER_APPROVE", "CANDIDATE_OFFER", offer.getId(), "SUCCESS");

        return CandidateOfferDto.from(offer);
    }

    @Transactional(readOnly = true)
    public String getOfferDownloadUrl(String sessionId, String candidateId, String email) {
        sessionService.getSession(sessionId, email);
        candidateInSession(sessionId, candidateId);
        CandidateOffer offer = offerRepository.findByCandidateId(candidateId)
                .orElseThrow(() -> ApiException.notFound("Offer not found", "No offer exists for this candidate"));
        return uploadService.getPresignedUrl(offer.getFileKey());
    }

    @Transactional(readOnly = true)
    public List<PipelineCandidateDto> getPipeline(Boolean awaitingApprovalOnly, String email) {
        User user = getUser(email);
        boolean canSeeAll = Authorities.has(Permission.SESSION_VIEW_ALL);

        List<Candidate> candidates;
        if (canSeeAll) {
            candidates = candidateRepository.findByReviewStatusNot(ReviewStatus.NONE);
        } else {
            List<ScreeningSession> mySessions = sessionRepository.findByUserEmailOrderByCreatedAtDesc(email);
            if (mySessions.isEmpty()) {
                return Collections.emptyList();
            }
            Set<String> sessionIds = new HashSet<>(mySessions.stream().map(ScreeningSession::getId).toList());
            candidates = candidateRepository.findBySessionIdInAndReviewStatusNot(sessionIds, ReviewStatus.NONE);
        }

        List<PipelineCandidateDto> result = new ArrayList<>();
        Instant now = Instant.now();

        for (Candidate c : candidates) {
            CandidateOffer offer = offerRepository.findByCandidateId(c.getId()).orElse(null);
            boolean isAwaitingApproval = c.getReviewStatus() == ReviewStatus.OFFER && offer != null && offer.getApprovedAt() == null;
            if (Boolean.TRUE.equals(awaitingApprovalOnly) && !isAwaitingApproval) {
                continue;
            }

            Instant changedAt = c.getReviewedAt() != null ? c.getReviewedAt() : (c.getCreatedAt() != null ? c.getCreatedAt() : now);
            long inStageDays = Math.max(0, Duration.between(changedAt, now).toDays());

            CandidateScore score = candidateScoreRepository.findByCandidateId(c.getId()).stream().findFirst().orElse(null);
            Double compositeScore = score != null ? score.getCompositeScore() : null;

            String nextStep = determineNextStep(c, offer);

            result.add(new PipelineCandidateDto(
                    c.getId(),
                    c.getSession() != null ? c.getSession().getId() : null,
                    c.getSession() != null ? c.getSession().getTitle() : "Unknown session",
                    c.getName(),
                    c.getEmail(),
                    compositeScore,
                    c.getReviewStatus(),
                    inStageDays,
                    nextStep,
                    changedAt,
                    offer != null ? offer.getFileName() : null,
                    offer != null && offer.getUploadedBy() != null ? offer.getUploadedBy().getFullName() : null,
                    offer != null && offer.getUploadedBy() != null ? offer.getUploadedBy().getId() : null
            ));
        }

        result.sort(Comparator.comparingLong(PipelineCandidateDto::inStageDays).reversed());
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getMyInterviews(String email) {
        User user = getUser(email);
        List<CandidateInterview> interviews = interviewRepository.findByInterviewerIdOrderByScheduledStartAsc(user.getId());
        Instant now = Instant.now();

        List<Map<String, Object>> waiting = new ArrayList<>();
        List<Map<String, Object>> upcoming = new ArrayList<>();
        List<Map<String, Object>> done = new ArrayList<>();

        for (CandidateInterview i : interviews) {
            Candidate candidate = candidateRepository.findById(i.getCandidateId()).orElse(null);
            String roleName = candidate != null && candidate.getSession() != null ? candidate.getSession().getTitle() : "Candidate";
            String candidateName = candidate != null ? candidate.getName() : "Candidate";

            Map<String, Object> item = new HashMap<>();
            item.put("id", i.getId());
            item.put("candidateId", i.getCandidateId());
            item.put("candidateName", candidateName);
            item.put("role", roleName);
            item.put("kind", i.getKind());
            item.put("scheduledStart", i.getScheduledStart());
            item.put("durationMinutes", i.getDurationMinutes());
            item.put("location", i.getLocation());
            item.put("status", i.getStatus());
            item.put("recommendation", i.getRecommendation());
            item.put("rating", i.getRating());
            item.put("feedbackSubmittedAt", i.getFeedbackSubmittedAt());

            if (i.getStatus() == InterviewStatus.COMPLETED) {
                done.add(item);
            } else if (i.getStatus() == InterviewStatus.SCHEDULED) {
                if (i.getScheduledStart().isBefore(now)) {
                    waiting.add(item);
                } else {
                    upcoming.add(item);
                }
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("waiting", waiting);
        response.put("upcoming", upcoming);
        response.put("done", done);
        return response;
    }

    private String determineNextStep(Candidate candidate, CandidateOffer offer) {
        if (candidate.getReviewStatus() == null) {
            return "Screened";
        }
        return switch (candidate.getReviewStatus()) {
            case SHORTLISTED -> "Mark as contacted";
            case CONTACTED -> "Schedule interview";
            case INTERVIEW_SCHEDULED -> {
                List<CandidateInterview> scheduled = interviewRepository.findByCandidateIdAndStatusOrderByScheduledStartAsc(candidate.getId(), InterviewStatus.SCHEDULED);
                if (!scheduled.isEmpty()) {
                    CandidateInterview next = scheduled.get(0);
                    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm").withZone(ZoneId.of("UTC"));
                    yield next.getKind() + " · " + fmt.format(next.getScheduledStart());
                }
                yield "Interview scheduled";
            }
            case INTERVIEWED -> "Move to offer";
            case OFFER -> offer != null ? "Waiting for approval" : "Upload offer";
            case OFFER_APPROVED -> "Done";
            default -> "Screened";
        };
    }

    /**
     * The three interview-driven stages follow the interviews: none left -> CONTACTED, any without
     * feedback -> INTERVIEW_SCHEDULED, all with feedback -> INTERVIEWED. Offers and earlier stages are untouched.
     */
    void recomputeInterviewStage(Candidate candidate, User actor) {
        ReviewStatus current = candidate.getReviewStatus();
        if (current.compareTo(ReviewStatus.CONTACTED) < 0 || current.compareTo(ReviewStatus.OFFER) >= 0) {
            return;
        }
        List<CandidateInterview> live = interviewRepository.findByCandidateIdOrderByScheduledStartAsc(candidate.getId())
                .stream().filter(i -> i.getStatus() != InterviewStatus.CANCELLED).toList();
        ReviewStatus next = live.isEmpty() ? ReviewStatus.CONTACTED
                : live.stream().anyMatch(i -> i.getStatus() == InterviewStatus.SCHEDULED) ? ReviewStatus.INTERVIEW_SCHEDULED
                : ReviewStatus.INTERVIEWED;
        if (next == current) {
            return;
        }
        candidate.setReviewStatus(next);
        candidate.setReviewedAt(Instant.now());
        candidateRepository.save(candidate);
        // scheduling already wrote its INTERVIEW_SCHEDULED event
        if (next == ReviewStatus.INTERVIEWED) {
            recordEvent(candidate.getId(), "INTERVIEWED", actor, "All interviews have feedback");
        } else if (next == ReviewStatus.CONTACTED) {
            recordEvent(candidate.getId(), "CONTACTED", actor, "No interviews left, back to contacted");
        }
    }

    private Candidate lockCandidate(String sessionId, String candidateId) {
        candidateInSession(sessionId, candidateId);
        return candidateRepository.findForUpdateById(candidateId)
                .orElseThrow(() -> ApiException.notFound("Candidate not found", "No candidate with id " + candidateId));
    }

    private void candidateInSession(String sessionId, String candidateId) {
        if (candidateRepository.findByIdAndSessionId(candidateId, sessionId).isEmpty()) {
            throw ApiException.notFound("Candidate not found", "No candidate " + candidateId + " in session " + sessionId);
        }
    }

    private static final Map<ReviewStatus, String> STAGE_LABEL = Map.of(
            ReviewStatus.NONE, "Screened", ReviewStatus.SHORTLISTED, "Shortlisted", ReviewStatus.CONTACTED, "Contacted",
            ReviewStatus.INTERVIEW_SCHEDULED, "Interviewing", ReviewStatus.INTERVIEWED, "Interviewed",
            ReviewStatus.OFFER, "Offer", ReviewStatus.OFFER_APPROVED, "Offer approved");

    private static void requireStage(Candidate candidate, ReviewStatus... allowed) {
        if (!Arrays.asList(allowed).contains(candidate.getReviewStatus())) {
            throw ApiException.validation("This candidate is at " + STAGE_LABEL.get(candidate.getReviewStatus())
                    + ", so this step isn't available", "Stage " + candidate.getReviewStatus());
        }
    }

    private void recordEvent(String candidateId, String stage, User actor, String summary) {
        CandidateStageEvent event = CandidateStageEvent.builder()
                .candidateId(candidateId)
                .stage(stage)
                .actorId(actor != null ? actor.getId() : null)
                .actorName(actor != null ? actor.getFullName() : null)
                .summary(summary)
                .build();
        stageEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> getInterviewers(InterviewKind kind) {
        List<Role> roles = (kind == null)
                ? List.of(Role.TECHNICAL_INTERVIEWER, Role.HR_INTERVIEWER)
                : (kind == InterviewKind.TECHNICAL ? List.of(Role.TECHNICAL_INTERVIEWER) : List.of(Role.HR_INTERVIEWER));
        return userRepository.findByRoleIn(roles).stream()
                .map(u -> Map.of(
                        "id", u.getId(),
                        "name", u.getFullName(),
                        "email", u.getEmail(),
                        "role", u.getRole().name()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public InterviewDetailDto getInterviewDetail(String interviewId, String email) {
        CandidateInterview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> ApiException.notFound("Interview not found", "No interview with id " + interviewId));

        Candidate candidate = candidateRepository.findById(interview.getCandidateId())
                .orElseThrow(() -> ApiException.notFound("Candidate not found", "No candidate found"));

        ScreeningSession session = candidate.getSession();
        User user = getUser(email);
        boolean isInterviewer = interview.getInterviewer() != null && interview.getInterviewer().getId().equals(user.getId());
        boolean isAdmin = user.getRole() == Role.ADMIN || user.getRole() == Role.HR_CHIEF;
        boolean isOwner = session != null && session.getUser() != null && session.getUser().getId().equals(user.getId());
        boolean canManagePipeline = Authorities.has(Permission.PIPELINE_MANAGE) && (isOwner || user.getRole() == Role.ADMIN);

        if (!isAdmin && !isInterviewer && !canManagePipeline) {
            throw ApiException.forbidden("Not authorized", "You cannot view this interview");
        }
        String recruiterName = (session != null && session.getUser() != null) ? session.getUser().getFullName() : null;

        List<CandidateScore> scores = candidateScoreRepository.findByCandidateId(candidate.getId());
        CandidateScore score = scores.isEmpty() ? null : scores.get(0);

        String resumeUrl = null;
        if (candidate.getResumeFileKey() != null) {
            try {
                resumeUrl = uploadService.getPresignedUrl(candidate.getResumeFileKey());
            } catch (Exception e) {
                log.warn("Failed to generate resume presigned URL: {}", e.getMessage());
            }
        }

        return new InterviewDetailDto(
                interview.getId(),
                candidate.getId(),
                candidate.getName(),
                candidate.getEmail(),
                session != null ? session.getId() : null,
                session != null ? session.getTitle() : "Candidate",
                recruiterName,
                interview.getKind(),
                interview.getInterviewer() != null ? interview.getInterviewer().getId() : null,
                interview.getInterviewer() != null ? interview.getInterviewer().getFullName() : null,
                interview.getScheduledStart(),
                interview.getDurationMinutes(),
                interview.getLocation(),
                interview.getNoteToInterviewer(),
                interview.getStatus(),
                interview.getRecommendation(),
                interview.getRating(),
                interview.getFeedbackAssessment(),
                interview.getFeedbackSubmittedAt(),
                score != null ? score.getCompositeScore() : null,
                score != null ? score.getRank() : null,
                score != null ? score.getStrengths() : null,
                score != null ? score.getGaps() : null,
                resumeUrl
        );
    }

    private User getUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> ApiException.notFound("User not found", "No user found for email " + email));
    }
}
