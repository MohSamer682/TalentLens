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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

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
        return stageEventRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId)
                .stream()
                .map(CandidateStageEventDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CandidateInterviewDto> getInterviews(String sessionId, String candidateId, String email) {
        sessionService.getSession(sessionId, email);
        return interviewRepository.findByCandidateIdOrderByScheduledStartAsc(candidateId)
                .stream()
                .map(CandidateInterviewDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<CandidateOfferDto> getOffer(String sessionId, String candidateId, String email) {
        sessionForOffer(sessionId, email);
        return offerRepository.findByCandidateId(candidateId).map(CandidateOfferDto::from);
    }

    @Transactional
    public void markContacted(String sessionId, String candidateId, String email) {
        sessionService.getSession(sessionId, email);
        Candidate candidate = candidateRepository.findByIdAndSessionId(candidateId, sessionId)
                .orElseThrow(() -> ApiException.notFound("Candidate not found", "No candidate with id " + candidateId));
        if (candidate.getReviewStatus() != ReviewStatus.SHORTLISTED) {
            throw ApiException.validation("Candidate cannot be marked as contacted", "Must be at SHORTLISTED stage");
        }
        User actor = getUser(email);
        candidate.setReviewStatus(ReviewStatus.CONTACTED);
        candidate.setReviewedAt(Instant.now());
        candidateRepository.save(candidate);

        recordEvent(candidateId, "CONTACTED", actor, "Contacted by " + actor.getFullName());
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
        sessionService.getSession(sessionId, email);
        Candidate candidate = candidateRepository.findByIdAndSessionId(candidateId, sessionId)
                .orElseThrow(() -> ApiException.notFound("Candidate not found", "No candidate with id " + candidateId));
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

        User actor = getUser(email);
        recordEvent(candidateId, "INTERVIEW_PLAN", actor, "Interviews planned: " + candidate.getInterviewPlan().replace(',', ' ') + " by " + actor.getFullName());
        auditService.record("INTERVIEW_PLAN", "CANDIDATE", candidateId, "SUCCESS");
        settleInterviewed(candidate);
        return new ArrayList<>(plan);
    }

    @Transactional
    public CandidateInterviewDto scheduleInterview(String sessionId, String candidateId, ScheduleInterviewRequest req, String email) {
        sessionService.getSession(sessionId, email);
        Candidate candidate = candidateRepository.findByIdAndSessionId(candidateId, sessionId)
                .orElseThrow(() -> ApiException.notFound("Candidate not found", "No candidate with id " + candidateId));

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

        User creator = getUser(email);
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

        recordEvent(candidateId, "INTERVIEW_SCHEDULED", creator,
                req.kind() + " interview scheduled with " + interviewer.getFullName() + " by " + creator.getFullName());
        auditService.record("INTERVIEW_SCHEDULE", "CANDIDATE_INTERVIEW", interview.getId(), "SUCCESS");

        return CandidateInterviewDto.from(interview);
    }

    @Transactional
    public CandidateInterviewDto rescheduleInterview(String sessionId, String candidateId, String interviewId, RescheduleInterviewRequest req, String email) {
        sessionService.getSession(sessionId, email);
        CandidateInterview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> ApiException.notFound("Interview not found", "No interview with id " + interviewId));

        if (!interview.getCandidateId().equals(candidateId)) {
            throw ApiException.validation("Mismatched candidate", "Interview does not belong to candidate");
        }
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

        User actor = getUser(email);
        recordEvent(candidateId, "INTERVIEW_RESCHEDULED", actor,
                interview.getKind() + " interview rescheduled by " + actor.getFullName());
        auditService.record("INTERVIEW_RESCHEDULE", "CANDIDATE_INTERVIEW", interviewId, "SUCCESS");

        return CandidateInterviewDto.from(interview);
    }

    @Transactional
    public void cancelInterview(String sessionId, String candidateId, String interviewId, String email) {
        sessionService.getSession(sessionId, email);
        CandidateInterview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> ApiException.notFound("Interview not found", "No interview with id " + interviewId));

        if (!interview.getCandidateId().equals(candidateId)) {
            throw ApiException.validation("Mismatched candidate", "Interview does not belong to candidate");
        }
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

        candidateRepository.findById(candidateId).ifPresent(this::settleInterviewed);
    }

    @Transactional
    public CandidateInterviewDto submitFeedback(String interviewId, SubmitFeedbackRequest req, String email) {
        CandidateInterview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> ApiException.notFound("Interview not found", "No interview with id " + interviewId));

        User interviewer = getUser(email);
        if (interviewer.getRole() != Role.ADMIN && (interview.getInterviewer() == null || !interview.getInterviewer().getId().equals(interviewer.getId()))) {
            throw ApiException.forbidden("Cannot submit feedback", "Only the assigned interviewer can submit feedback");
        }
        if (interview.getStatus() == InterviewStatus.COMPLETED) {
            throw ApiException.validation("Feedback already submitted", "Feedback cannot be changed after submission");
        }
        if (interview.getScheduledStart().isAfter(Instant.now())) {
            throw ApiException.validation("Interview not yet started", "Feedback can only be submitted once the interview has started");
        }

        interview.setRecommendation(req.recommendation());
        interview.setRating(req.rating());
        interview.setFeedbackAssessment(req.assessment().trim());
        interview.setFeedbackSubmittedAt(Instant.now());
        interview.setStatus(InterviewStatus.COMPLETED);
        interview.setUpdatedAt(Instant.now());
        interview = interviewRepository.save(interview);

        recordEvent(interview.getCandidateId(), "FEEDBACK_SUBMITTED", interviewer,
                "Feedback submitted by " + interviewer.getFullName() + ": " + req.recommendation());
        auditService.record("INTERVIEW_FEEDBACK_SUBMIT", "CANDIDATE_INTERVIEW", interviewId, "SUCCESS");

        candidateRepository.findById(interview.getCandidateId()).ifPresent(this::settleInterviewed);

        return CandidateInterviewDto.from(interview);
    }

    @Transactional
    public void moveToOffer(String sessionId, String candidateId, String email) {
        sessionService.getSession(sessionId, email);
        Candidate candidate = candidateRepository.findByIdAndSessionId(candidateId, sessionId)
                .orElseThrow(() -> ApiException.notFound("Candidate not found", "No candidate with id " + candidateId));

        if (candidate.getReviewStatus() != ReviewStatus.INTERVIEWED) {
            throw ApiException.validation("Cannot move to offer", "Candidate must be at INTERVIEWED stage");
        }

        User actor = getUser(email);
        candidate.setReviewStatus(ReviewStatus.OFFER);
        candidate.setReviewedAt(Instant.now());
        candidateRepository.save(candidate);

        recordEvent(candidateId, "OFFER", actor, "Moved to offer by " + actor.getFullName());
        auditService.record("CANDIDATE_MOVE_TO_OFFER", "CANDIDATE", candidateId, "SUCCESS");
    }

    @Transactional
    public CandidateOfferDto uploadOffer(String sessionId, String candidateId, MultipartFile file, boolean requiresDirectorApproval, String email) {
        sessionService.getSession(sessionId, email);
        Candidate candidate = candidateRepository.findByIdAndSessionId(candidateId, sessionId)
                .orElseThrow(() -> ApiException.notFound("Candidate not found", "No candidate with id " + candidateId));

        if (candidate.getReviewStatus() != ReviewStatus.OFFER) {
            throw ApiException.validation("Cannot upload offer", "Candidate must be at OFFER stage");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.validation("Could not read file", e.getMessage());
        }

        if (bytes.length > 10L * 1024 * 1024) {
            throw ApiException.validation("Offer PDF too large", "Maximum file size is 10 MB");
        }
        if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            throw ApiException.validation("File is not a valid PDF", "Uploaded file must be a PDF document");
        }

        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "offer.pdf";
        String fileKey = uploadService.uploadOfferFile(sessionId, candidateId, fileName, bytes);

        User uploader = getUser(email);
        CandidateOffer offer = offerRepository.findByCandidateId(candidateId).orElse(null);
        if (offer == null) {
            offer = CandidateOffer.builder().candidateId(candidateId).build();
        }
        // A new or revised offer restarts the approval chain
        offer.setFileKey(fileKey);
        offer.setFileName(fileName);
        offer.setSizeBytes(bytes.length);
        offer.setUploadedBy(uploader);
        offer.setUploadedAt(Instant.now());
        offer.setRequiresDirectorApproval(requiresDirectorApproval);
        offer.setApprovedBy(null);
        offer.setApprovedAt(null);
        offer.setDirectorApprovedBy(null);
        offer.setDirectorApprovedAt(null);
        offer.setRejectedBy(null);
        offer.setRejectedAt(null);
        offer.setRejectionNote(null);
        offer = offerRepository.save(offer);

        recordEvent(candidateId, "OFFER", uploader, "Offer uploaded by " + uploader.getFullName()
                + (requiresDirectorApproval ? " (department director approval required)" : ""));
        auditService.record("OFFER_UPLOAD", "CANDIDATE_OFFER", offer.getId(), "SUCCESS");

        return CandidateOfferDto.from(offer);
    }

    /**
     * One call, two possible steps. While the department director's sign-off is outstanding only a
     * department director (or admin) can give it; after that, only the HR chief (or admin) gives the final one.
     */
    @Transactional
    public CandidateOfferDto approveOffer(String sessionId, String candidateId, String email) {
        sessionForOffer(sessionId, email);
        CandidateOffer offer = offerRepository.findByCandidateId(candidateId)
                .orElseThrow(() -> ApiException.notFound("Offer not found", "No offer exists for this candidate"));

        User approver = getUser(email);
        if (offer.getUploadedBy() != null && offer.getUploadedBy().getId().equals(approver.getId())) {
            throw ApiException.validation("Cannot approve your own offer", "A different approver is required to approve this offer");
        }
        if (offer.getApprovedAt() != null) {
            throw ApiException.validation("Offer already approved", "This offer has already been approved");
        }
        if (offer.getRejectedAt() != null) {
            throw ApiException.validation("Offer was rejected", "The recruiter has to upload a revised offer first");
        }

        if (directorStepPending(offer)) {
            requireRole(approver, "Waiting for the department director", Role.DEPARTMENT_DIRECTOR);
            offer.setDirectorApprovedBy(approver);
            offer.setDirectorApprovedAt(Instant.now());
            offer = offerRepository.save(offer);
            recordEvent(candidateId, "OFFER_DIRECTOR_APPROVED", approver, "Offer approved by department director " + approver.getFullName());
            auditService.record("OFFER_DIRECTOR_APPROVE", "CANDIDATE_OFFER", offer.getId(), "SUCCESS");
            return CandidateOfferDto.from(offer);
        }

        requireRole(approver, "Only the HR chief can give the final approval", Role.HR_CHIEF);
        offer.setApprovedBy(approver);
        offer.setApprovedAt(Instant.now());
        offer = offerRepository.save(offer);

        Candidate candidate = candidateRepository.findByIdAndSessionId(candidateId, sessionId)
                .orElseThrow(() -> ApiException.notFound("Candidate not found", "No candidate with id " + candidateId));
        candidate.setReviewStatus(ReviewStatus.OFFER_APPROVED);
        candidate.setReviewedAt(Instant.now());
        candidateRepository.save(candidate);

        recordEvent(candidateId, "OFFER_APPROVED", approver, "Offer approved by " + approver.getFullName());
        auditService.record("OFFER_APPROVE", "CANDIDATE_OFFER", offer.getId(), "SUCCESS");

        return CandidateOfferDto.from(offer);
    }

    /** Whoever holds the current approval step may send the offer back; the note is optional. */
    @Transactional
    public CandidateOfferDto rejectOffer(String sessionId, String candidateId, RejectOfferRequest req, String email) {
        sessionForOffer(sessionId, email);
        CandidateOffer offer = offerRepository.findByCandidateId(candidateId)
                .orElseThrow(() -> ApiException.notFound("Offer not found", "No offer exists for this candidate"));
        if (offer.getApprovedAt() != null) {
            throw ApiException.validation("Offer already approved", "An approved offer cannot be rejected");
        }
        if (offer.getRejectedAt() != null) {
            throw ApiException.validation("Offer already rejected", "Waiting for the recruiter to upload a revised offer");
        }

        User rejecter = getUser(email);
        if (directorStepPending(offer)) {
            requireRole(rejecter, "Waiting for the department director", Role.DEPARTMENT_DIRECTOR);
        } else {
            requireRole(rejecter, "Only the HR chief can reject at this step", Role.HR_CHIEF);
        }

        String note = req == null || req.note() == null || req.note().isBlank() ? null : req.note().trim();
        offer.setRejectedBy(rejecter);
        offer.setRejectedAt(Instant.now());
        offer.setRejectionNote(note);
        offer = offerRepository.save(offer);

        recordEvent(candidateId, "OFFER_REJECTED", rejecter,
                "Offer rejected by " + rejecter.getFullName() + (note != null ? ": " + note : ""));
        auditService.record("OFFER_REJECT", "CANDIDATE_OFFER", offer.getId(), "SUCCESS");

        return CandidateOfferDto.from(offer);
    }

    @Transactional
    public CandidateOfferDto markOfferSent(String sessionId, String candidateId, String email) {
        sessionService.getSession(sessionId, email);
        Candidate candidate = candidateInStage(sessionId, candidateId, ReviewStatus.OFFER_APPROVED, "Offer must be approved before it is sent");
        CandidateOffer offer = offerOf(candidateId);

        offer.setSentAt(Instant.now());
        offerRepository.save(offer);
        User actor = getUser(email);
        moveTo(candidate, ReviewStatus.OFFER_SENT, "OFFER_SENT", actor, "Offer sent to the candidate by " + actor.getFullName());
        return CandidateOfferDto.from(offer);
    }

    @Transactional
    public CandidateOfferDto answerOffer(String sessionId, String candidateId, OfferAnswerRequest req, String email) {
        sessionService.getSession(sessionId, email);
        Candidate candidate = candidateInStage(sessionId, candidateId, ReviewStatus.OFFER_SENT, "The offer must be sent before the candidate can answer");
        CandidateOffer offer = offerOf(candidateId);

        boolean accepted = Boolean.TRUE.equals(req.accepted());
        offer.setCandidateResponse(accepted ? "ACCEPTED" : "DECLINED");
        offer.setRespondedAt(Instant.now());
        offerRepository.save(offer);
        User actor = getUser(email);
        moveTo(candidate, accepted ? ReviewStatus.ACCEPTED : ReviewStatus.DECLINED, accepted ? "ACCEPTED" : "DECLINED", actor,
                "Candidate " + (accepted ? "accepted" : "declined") + " the offer, recorded by " + actor.getFullName());
        return CandidateOfferDto.from(offer);
    }

    @Transactional
    public CandidateOfferDto saveJoining(String sessionId, String candidateId, JoiningRequest req, String email) {
        sessionService.getSession(sessionId, email);
        Candidate candidate = candidateRepository.findByIdAndSessionId(candidateId, sessionId)
                .orElseThrow(() -> ApiException.notFound("Candidate not found", "No candidate with id " + candidateId));
        if (candidate.getReviewStatus() != ReviewStatus.ACCEPTED && candidate.getReviewStatus() != ReviewStatus.JOINING_SET) {
            throw ApiException.validation("Cannot set a joining date", "The candidate must have accepted the offer");
        }
        CandidateOffer offer = offerOf(candidateId);

        offer.setJoiningDate(req.joiningDate());
        offer.setJoiningNotes(req.notes() == null || req.notes().isBlank() ? null : req.notes().trim());
        offerRepository.save(offer);
        User actor = getUser(email);
        if (candidate.getReviewStatus() == ReviewStatus.ACCEPTED) {
            moveTo(candidate, ReviewStatus.JOINING_SET, "JOINING_SET", actor, "Joining date set to " + req.joiningDate() + " by " + actor.getFullName());
        } else {
            recordEvent(candidateId, "JOINING_SET", actor, "Joining details changed to " + req.joiningDate() + " by " + actor.getFullName());
        }
        return CandidateOfferDto.from(offer);
    }

    @Transactional(readOnly = true)
    public String getOfferDownloadUrl(String sessionId, String candidateId, String email) {
        sessionForOffer(sessionId, email);
        CandidateOffer offer = offerRepository.findByCandidateId(candidateId)
                .orElseThrow(() -> ApiException.notFound("Offer not found", "No offer exists for this candidate"));
        return uploadService.getPresignedUrl(offer.getFileKey());
    }

    @Transactional(readOnly = true)
    public List<PipelineCandidateDto> getPipeline(Boolean awaitingApprovalOnly, String email) {
        User user = getUser(email);
        boolean canSeeAll = user.getRole() == Role.ADMIN || user.getRole() == Role.HR_CHIEF || user.getRole() == Role.DEPARTMENT_DIRECTOR;

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
            String offerStep = offerStep(c, offer);
            boolean isAwaitingApproval = "DIRECTOR".equals(offerStep) || "HR_CHIEF".equals(offerStep);
            if (Boolean.TRUE.equals(awaitingApprovalOnly) && !isAwaitingApproval) {
                continue;
            }

            Instant changedAt = c.getReviewedAt() != null ? c.getReviewedAt() : (c.getCreatedAt() != null ? c.getCreatedAt() : now);
            long inStageDays = Math.max(0, Duration.between(changedAt, now).toDays());

            CandidateScore score = candidateScoreRepository.findByCandidateId(c.getId()).stream().findFirst().orElse(null);
            Double compositeScore = score != null ? score.getCompositeScore() : null;

            String nextStep = determineNextStep(c, offer, offerStep);

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
                    offer != null && offer.getUploadedBy() != null ? offer.getUploadedBy().getId() : null,
                    offerStep
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
            item.put("placeType", i.getPlaceType());
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

    /** Who must act on the offer next: DIRECTOR, HR_CHIEF, or REJECTED when the recruiter has to revise it. */
    private String offerStep(Candidate candidate, CandidateOffer offer) {
        if (candidate.getReviewStatus() != ReviewStatus.OFFER || offer == null || offer.getApprovedAt() != null) {
            return null;
        }
        if (offer.getRejectedAt() != null) {
            return "REJECTED";
        }
        return directorStepPending(offer) ? "DIRECTOR" : "HR_CHIEF";
    }

    private boolean directorStepPending(CandidateOffer offer) {
        return offer.isRequiresDirectorApproval() && offer.getDirectorApprovedAt() == null;
    }

    private String determineNextStep(Candidate candidate, CandidateOffer offer, String offerStep) {
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
                yield "Schedule the next interview";
            }
            case INTERVIEWED -> "Move to offer";
            case OFFER -> {
                if (offer == null) yield "Upload offer";
                yield switch (offerStep == null ? "" : offerStep) {
                    case "REJECTED" -> "Revise offer";
                    case "DIRECTOR" -> "Waiting for department director";
                    default -> "Waiting for HR chief";
                };
            }
            case OFFER_APPROVED -> "Send offer";
            case OFFER_SENT -> "Record candidate's answer";
            case ACCEPTED -> "Set joining date";
            case JOINING_SET -> "Joining " + (offer != null && offer.getJoiningDate() != null ? offer.getJoiningDate() : "");
            case DECLINED -> "Closed: offer declined";
            default -> "Screened";
        };
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
        List<Role> roles = kind == null
                ? List.of(Role.TECHNICAL_INTERVIEWER, Role.HR_INTERVIEWER, Role.DEPARTMENT_DIRECTOR, Role.CEO, Role.CHAIRMAN)
                : switch (kind) {
                    case TECHNICAL -> List.of(Role.TECHNICAL_INTERVIEWER);
                    case HR -> List.of(Role.HR_INTERVIEWER);
                    case MANAGEMENT -> List.of(Role.DEPARTMENT_DIRECTOR, Role.CEO, Role.CHAIRMAN);
                };
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
                interview.getPlaceType(),
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

    private static void requireRole(User user, String message, Role role) {
        if (user.getRole() != Role.ADMIN && user.getRole() != role) {
            throw ApiException.forbidden(message, "Your role cannot act on this step");
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
            recordEvent(candidate.getId(), "INTERVIEWED", null, "All planned interviews completed");
        }
    }

    private Candidate candidateInStage(String sessionId, String candidateId, ReviewStatus required, String message) {
        Candidate candidate = candidateRepository.findByIdAndSessionId(candidateId, sessionId)
                .orElseThrow(() -> ApiException.notFound("Candidate not found", "No candidate with id " + candidateId));
        if (candidate.getReviewStatus() != required) {
            throw ApiException.validation("Wrong stage", message);
        }
        return candidate;
    }

    private CandidateOffer offerOf(String candidateId) {
        return offerRepository.findByCandidateId(candidateId)
                .orElseThrow(() -> ApiException.notFound("Offer not found", "No offer exists for this candidate"));
    }

    private void moveTo(Candidate candidate, ReviewStatus next, String eventStage, User actor, String summary) {
        candidate.setReviewStatus(next);
        candidate.setReviewedAt(Instant.now());
        candidateRepository.save(candidate);
        recordEvent(candidate.getId(), eventStage, actor, summary);
        auditService.record("CANDIDATE_" + eventStage, "CANDIDATE", candidate.getId(), "SUCCESS");
    }

    /**
     * Approvers read the session of an offer without being able to browse sessions: holding
     * OFFER_APPROVE is enough here. Everyone else needs normal read access to the session.
     */
    private void sessionForOffer(String sessionId, String email) {
        if (Authorities.has(Permission.OFFER_APPROVE)) {
            if (!sessionRepository.existsById(sessionId)) {
                throw ApiException.notFound("Session not found", "No session with id " + sessionId);
            }
            return;
        }
        sessionService.getSession(sessionId, email);
    }

    private User getUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> ApiException.notFound("User not found", "No user found for email " + email));
    }
}
