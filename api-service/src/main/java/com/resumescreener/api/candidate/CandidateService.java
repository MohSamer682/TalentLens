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
import com.resumescreener.api.candidate.dto.CandidateNoteDto;
import com.resumescreener.common.session.CandidateNote;
import com.resumescreener.common.session.CandidateNoteRepository;
import com.resumescreener.common.session.CandidateStageEvent;
import com.resumescreener.common.session.CandidateStageEventRepository;
import com.resumescreener.common.user.User;
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
    private final CandidateNoteRepository candidateNoteRepository;
    private final CandidateStageEventRepository candidateStageEventRepository;

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
        ScreeningSession session = sessionService.getOwnedSession(sessionId, email);
        if (session.getStatus() == SessionStatus.RUNNING) {
            throw ApiException.validation("Wait for scoring to finish before reviewing",
                    "Session " + sessionId + " is in status RUNNING");
        }
        return session;
    }

    private ReviewResult apply(String sessionId, List<Candidate> candidates, ReviewStatus status, String email) {
        User reviewer = userRepository.findByEmailIgnoreCase(email).orElse(null);
        String reviewerId = status == ReviewStatus.NONE ? null : (reviewer != null ? reviewer.getId() : null);
        String action = status == ReviewStatus.SHORTLISTED ? "CANDIDATE_SHORTLIST" : "CANDIDATE_UNSHORTLIST";
        int changed = 0;
        for (Candidate c : candidates) {
            if (c.getReviewStatus() == status) {
                continue;
            }
            // Once a candidate is in the hiring pipeline, shortlisting or unshortlisting must not drag them back
            ReviewStatus current = c.getReviewStatus();
            if (current != ReviewStatus.NONE && current != ReviewStatus.SHORTLISTED) {
                continue;
            }
            c.setReviewStatus(status);
            c.setReviewedBy(reviewerId);
            c.setReviewedAt(status == ReviewStatus.NONE ? null : Instant.now());
            auditService.record(action, "candidate", c.getId(), "success");
            if (status == ReviewStatus.SHORTLISTED) {
                CandidateStageEvent event = CandidateStageEvent.builder()
                        .candidateId(c.getId())
                        .stage("SHORTLISTED")
                        .actorId(reviewerId)
                        .actorName(reviewer != null ? reviewer.getFullName() : null)
                        .summary("Shortlisted by " + (reviewer != null ? reviewer.getFullName() : "Recruiter"))
                        .build();
                candidateStageEventRepository.save(event);
            }
            changed++;
        }
        candidateRepository.saveAll(candidates);
        return new ReviewResult(changed, candidateRepository.countBySessionIdAndReviewStatusNot(sessionId, ReviewStatus.NONE));
    }

    @Transactional(readOnly = true)
    public List<CandidateNoteDto> listNotes(String sessionId, String candidateId, String email) {
        sessionService.getSession(sessionId, email);
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> ApiException.notFound("User not found", "User not found"));
        boolean isAdmin = user.getRole() == com.resumescreener.common.user.Role.ADMIN;
        return candidateNoteRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId)
                .stream()
                .map(n -> CandidateNoteDto.from(n, user.getId(), isAdmin))
                .toList();
    }

    @Transactional
    public CandidateNoteDto addNote(String sessionId, String candidateId, String content, String email) {
        sessionService.getSession(sessionId, email);
        Candidate candidate = candidateRepository.findByIdAndSessionId(candidateId, sessionId)
                .orElseThrow(() -> ApiException.notFound("Candidate not found in this session", "Candidate not found"));
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> ApiException.notFound("User not found", "User not found"));
        CandidateNote note = CandidateNote.builder()
                .candidateId(candidate.getId())
                .author(user)
                .content(content.trim())
                .build();
        note = candidateNoteRepository.save(note);
        auditService.record("CANDIDATE_NOTE_ADD", "CANDIDATE_NOTE", note.getId(), "SUCCESS", "Added note to candidate " + candidateId);
        boolean isAdmin = user.getRole() == com.resumescreener.common.user.Role.ADMIN;
        return CandidateNoteDto.from(note, user.getId(), isAdmin);
    }

    @Transactional
    public void deleteNote(String sessionId, String candidateId, String noteId, String email) {
        sessionService.getSession(sessionId, email);
        CandidateNote note = candidateNoteRepository.findById(noteId)
                .orElseThrow(() -> ApiException.notFound("Note not found", "Note with id " + noteId + " does not exist"));
        if (!note.getCandidateId().equals(candidateId)) {
            throw ApiException.validation("Note does not belong to this candidate", "Mismatched candidate id");
        }
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> ApiException.notFound("User not found", "User not found"));
        boolean isAdmin = user.getRole() == com.resumescreener.common.user.Role.ADMIN;
        if (!isAdmin && (note.getAuthor() == null || !note.getAuthor().getId().equals(user.getId()))) {
            throw ApiException.forbidden("Cannot delete note", "You can only delete your own notes");
        }
        candidateNoteRepository.delete(note);
        auditService.record("CANDIDATE_NOTE_DELETE", "CANDIDATE_NOTE", noteId, "SUCCESS", "Deleted note from candidate " + candidateId);
    }
}
