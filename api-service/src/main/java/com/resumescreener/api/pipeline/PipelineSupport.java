package com.resumescreener.api.pipeline;

import com.resumescreener.api.audit.AuditService;
import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.common.Authorities;
import com.resumescreener.api.session.SessionService;
import com.resumescreener.common.session.Candidate;
import com.resumescreener.common.session.CandidateOffer;
import com.resumescreener.common.session.CandidateRepository;
import com.resumescreener.common.session.CandidateStageEvent;
import com.resumescreener.common.session.CandidateStageEventRepository;
import com.resumescreener.common.session.ReviewStatus;
import com.resumescreener.common.session.ScreeningSessionRepository;
import com.resumescreener.common.user.Permission;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Stage moves, timeline events and lookups shared by the pipeline services. */
@Component
@RequiredArgsConstructor
class PipelineSupport {

    private final CandidateRepository candidateRepository;
    private final CandidateStageEventRepository stageEventRepository;
    private final ScreeningSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final AuditService auditService;

    void recordEvent(String candidateId, String stage, User actor, String summary) {
        CandidateStageEvent event = CandidateStageEvent.builder()
                .candidateId(candidateId)
                .stage(stage)
                .actorId(actor != null ? actor.getId() : null)
                .actorName(actor != null ? actor.getFullName() : null)
                .summary(summary)
                .build();
        stageEventRepository.save(event);
    }

    Candidate candidateInStage(String sessionId, String candidateId, ReviewStatus required, String message) {
        Candidate candidate = lockCandidate(sessionId, candidateId);
        if (candidate.getReviewStatus() != required) {
            throw ApiException.validation("Wrong stage", message);
        }
        return candidate;
    }

    Candidate lockCandidate(String sessionId, String candidateId) {
        candidateInSession(sessionId, candidateId);
        return candidateRepository.findForUpdateById(candidateId)
                .orElseThrow(() -> ApiException.notFound("Candidate not found", "No candidate with id " + candidateId));
    }

    void candidateInSession(String sessionId, String candidateId) {
        if (candidateRepository.findByIdAndSessionId(candidateId, sessionId).isEmpty()) {
            throw ApiException.notFound("Candidate not found", "No candidate " + candidateId + " in session " + sessionId);
        }
    }

    void moveTo(Candidate candidate, ReviewStatus next, String eventStage, User actor, String summary) {
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
    void sessionForOffer(String sessionId, String email) {
        if (Authorities.has(Permission.OFFER_APPROVE)) {
            if (!sessionRepository.existsById(sessionId)) {
                throw ApiException.notFound("Session not found", "No session with id " + sessionId);
            }
            return;
        }
        sessionService.getSession(sessionId, email);
    }

    User getUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> ApiException.notFound("User not found", "No user found for email " + email));
    }

    static void requireRole(User user, String message, Role role) {
        if (user.getRole() != Role.ADMIN && user.getRole() != role) {
            throw ApiException.forbidden(message, "Your role cannot act on this step");
        }
    }

    static boolean directorStepPending(CandidateOffer offer) {
        return offer.isRequiresDirectorApproval() && offer.getDirectorApprovedAt() == null;
    }
}
