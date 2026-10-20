package com.resumescreener.api.pipeline;

import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.common.Authorities;
import com.resumescreener.api.upload.MinioUploadService;
import com.resumescreener.api.candidate.dto.InterviewDetailDto;
import com.resumescreener.api.candidate.dto.PipelineCandidateDto;
import com.resumescreener.common.reschedule.InterviewRescheduleRequestRepository;
import com.resumescreener.common.session.*;
import com.resumescreener.common.user.Permission;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/** Read models for the pipeline board, an interviewer's own list and the interview detail page. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PipelineQueryService {

    private final CandidateRepository candidateRepository;
    private final CandidateScoreRepository candidateScoreRepository;
    private final CandidateInterviewRepository interviewRepository;
    private final CandidateOfferRepository offerRepository;
    private final ScreeningSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final MinioUploadService uploadService;
    private final PipelineSupport support;
    private final InterviewRescheduleRequestRepository rescheduleRepository;


    @Transactional(readOnly = true)
    public List<PipelineCandidateDto> getPipeline(Boolean awaitingApprovalOnly, String email) {
        User user = support.getUser(email);
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

        // One query per table instead of one per candidate
        List<String> ids = candidates.stream().map(Candidate::getId).toList();
        Map<String, CandidateOffer> offers = offerRepository.findByCandidateIdIn(ids).stream()
                .collect(Collectors.toMap(CandidateOffer::getCandidateId, o -> o));
        Map<String, Double> scores = new HashMap<>();
        candidateScoreRepository.findByCandidateIdIn(ids)
                .forEach(s -> scores.putIfAbsent(s.getCandidate().getId(), s.getCompositeScore()));
        Set<String> rescheduleRequested = new HashSet<>(rescheduleRepository.candidateIdsWithPendingRequest(ids));
        Map<String, CandidateInterview> nextInterview = new HashMap<>();
        interviewRepository.findByCandidateIdInAndStatusOrderByScheduledStartAsc(ids, InterviewStatus.SCHEDULED)
                .forEach(i -> nextInterview.putIfAbsent(i.getCandidateId(), i));

        List<PipelineCandidateDto> result = new ArrayList<>();
        Instant now = Instant.now();

        for (Candidate c : candidates) {
            CandidateOffer offer = offers.get(c.getId());
            String offerStep = offerStep(c, offer);
            boolean isAwaitingApproval = "DIRECTOR".equals(offerStep) || "HR_CHIEF".equals(offerStep);
            if (Boolean.TRUE.equals(awaitingApprovalOnly) && !isAwaitingApproval) {
                continue;
            }

            Instant changedAt = c.getReviewedAt() != null ? c.getReviewedAt() : (c.getCreatedAt() != null ? c.getCreatedAt() : now);
            long inStageDays = Math.max(0, Duration.between(changedAt, now).toDays());

            Double compositeScore = scores.get(c.getId());

            String nextStep = determineNextStep(c, offer, offerStep, nextInterview.get(c.getId()));

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
                    offerStep,
                    rescheduleRequested.contains(c.getId())
            ));
        }

        result.sort(Comparator.comparingLong(PipelineCandidateDto::inStageDays).reversed());
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getMyInterviews(String email) {
        User user = support.getUser(email);
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
        return PipelineSupport.directorStepPending(offer) ? "DIRECTOR" : "HR_CHIEF";
    }


    private String determineNextStep(Candidate candidate, CandidateOffer offer, String offerStep, CandidateInterview next) {
        if (candidate.getReviewStatus() == null) {
            return "Screened";
        }
        return switch (candidate.getReviewStatus()) {
            case SHORTLISTED -> "Mark as contacted";
            case CONTACTED -> "Schedule interview";
            case INTERVIEW_SCHEDULED -> {
                if (next != null) {
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

    @Transactional(readOnly = true)
    public List<Map<String, String>> getInterviewers(InterviewKind kind) {
        List<Role> roles = kind == null
                ? List.of(Role.TECHNICAL_INTERVIEWER, Role.HR_INTERVIEWER, Role.DEPARTMENT_DIRECTOR, Role.CEO, Role.CHAIRMAN)
                : switch (kind) {
                    case TECHNICAL -> List.of(Role.TECHNICAL_INTERVIEWER);
                    case HR -> List.of(Role.HR_INTERVIEWER);
                    case MANAGEMENT -> List.of(Role.DEPARTMENT_DIRECTOR, Role.CEO, Role.CHAIRMAN);
                };
        return userRepository.findByRoleInAndStatus(roles, com.resumescreener.common.user.UserStatus.ACTIVE).stream()
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
        User user = support.getUser(email);
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
}
