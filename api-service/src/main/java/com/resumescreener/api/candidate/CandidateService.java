package com.resumescreener.api.candidate;

import com.resumescreener.api.audit.AuditService;
import com.resumescreener.api.candidate.dto.ResumeFileDto;
import com.resumescreener.api.candidate.dto.ReviewResult;
import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.session.SessionService;
import com.resumescreener.common.session.Candidate;
import com.resumescreener.common.session.CandidateRepository;
import com.resumescreener.common.session.CandidateScore;
import com.resumescreener.common.session.CandidateScoreRepository;
import com.resumescreener.common.session.CandidateStatus;
import com.resumescreener.common.session.ReviewStatus;
import com.resumescreener.common.session.ScreeningSession;
import com.resumescreener.common.session.SessionStatus;
import com.resumescreener.common.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CandidateService {

    private final SessionService sessionService;
    private final CandidateRepository candidateRepository;
    private final CandidateScoreRepository candidateScoreRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public Page<ResumeFileDto> listResumes(String sessionId, CandidateStatus status, Pageable pageable, String email) {
        sessionService.getSession(sessionId, email);
        var page = status == null
                ? candidateRepository.findBySessionIdOrderByCreatedAtDesc(sessionId, pageable)
                : candidateRepository.findBySessionIdAndStatusOrderByCreatedAtDesc(sessionId, status, pageable);
        return page.map(ResumeFileDto::from);
    }

    /** All or nothing: an id outside this session fails the whole request before anything is saved. */
    @Transactional
    public ReviewResult review(String sessionId, Collection<String> candidateIds, ReviewStatus status, String email) {
        ScreeningSession session = reviewableSession(sessionId, email);
        List<Candidate> candidates = candidateRepository.findByIdInAndSessionId(candidateIds, session.getId());
        if (candidates.size() != new HashSet<>(candidateIds).size()) {
            throw ApiException.notFound("Candidate not found in this session",
                    "One or more candidates do not belong to session " + sessionId);
        }
        if (status == ReviewStatus.SHORTLISTED
                && candidates.stream().anyMatch(c -> c.getStatus() != CandidateStatus.SCORED)) {
            throw ApiException.validation("Only scored resumes can be shortlisted",
                    "Pending or unreadable resumes cannot be shortlisted");
        }
        return apply(sessionId, candidates, status, email);
    }

    /** Shortlists ranks 1..count; candidates already shortlisted further down stay as they are. */
    @Transactional
    public ReviewResult shortlistTop(String sessionId, int count, String email) {
        reviewableSession(sessionId, email);
        List<Candidate> top = candidateScoreRepository.findTop200ByCandidateSessionIdAndRankNotNullOrderByRankAsc(sessionId)
                .stream()
                .map(CandidateScore::getCandidate)
                .filter(c -> c.getStatus() == CandidateStatus.SCORED)
                .limit(count)
                .toList();
        return apply(sessionId, top, ReviewStatus.SHORTLISTED, email);
    }

    private ScreeningSession reviewableSession(String sessionId, String email) {
        ScreeningSession session = sessionService.getSession(sessionId, email);
        if (session.getStatus() == SessionStatus.RUNNING) {
            throw ApiException.validation("Wait for scoring to finish before reviewing",
                    "Session " + sessionId + " is in status RUNNING");
        }
        return session;
    }

    private ReviewResult apply(String sessionId, List<Candidate> candidates, ReviewStatus status, String email) {
        String reviewerId = status == ReviewStatus.NONE ? null
                : userRepository.findByEmailIgnoreCase(email).map(u -> u.getId()).orElse(null);
        String action = status == ReviewStatus.SHORTLISTED ? "CANDIDATE_SHORTLIST" : "CANDIDATE_UNSHORTLIST";
        int changed = 0;
        for (Candidate c : candidates) {
            if (c.getReviewStatus() == status) {
                continue;
            }
            c.setReviewStatus(status);
            c.setReviewedBy(reviewerId);
            c.setReviewedAt(status == ReviewStatus.NONE ? null : Instant.now());
            auditService.record(action, "candidate", c.getId(), "success");
            changed++;
        }
        candidateRepository.saveAll(candidates);
        return new ReviewResult(changed, candidateRepository.countBySessionIdAndReviewStatus(sessionId, ReviewStatus.SHORTLISTED));
    }
}
