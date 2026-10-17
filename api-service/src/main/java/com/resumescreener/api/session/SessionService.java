package com.resumescreener.api.session;

import com.resumescreener.api.common.ApiException;
import com.resumescreener.common.audit.*;
import com.resumescreener.common.session.*;
import com.resumescreener.common.user.*;
import com.resumescreener.common.session.SessionStatus;
import com.resumescreener.api.session.dto.*;
import com.resumescreener.api.upload.MinioUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.EntityGraph;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final ScreeningSessionRepository sessionRepository;
    private final ScoringWeightsRepository weightsRepository;
    private final CandidateRepository candidateRepository;
    private final UserRepository userRepository;
    private final MinioUploadService minioUploadService;

    /** Composite scores are weighted sums, so weights that do not total 100 push scores outside 0-100. */
    private static void requireWeightsTotal100(WeightsDto w) {
        double total = 0;
        for (Double d : new Double[]{w.getTechnicalSkills(), w.getExperience(), w.getEducation(),
                w.getSoftSkills(), w.getCultureFit()}) {
            if (d == null) {
                throw ApiException.validation("Weights are incomplete", "All five weights are required");
            }
            total += d;
        }
        if (Math.abs(total - 100.0) > 0.01) {
            throw ApiException.validation("Weights must total 100", "Weights total " + total);
        }
    }

    @Transactional
    public ScreeningSession createSession(CreateSessionRequest request, String userEmail) {
        if (request.getWeights() != null) requireWeightsTotal100(request.getWeights());
        User user = userRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> ApiException.notFound("User not found",
                        "No user exists for email " + userEmail));

        ScreeningSession session = ScreeningSession.builder()
                .user(user)
                .title(request.getTitle())
                .description(request.getDescription())
                .status(SessionStatus.PENDING)
                .createdAt(java.time.Instant.now())
                .build();

        if (request.getWeights() != null) {
            ScoringWeights weights = ScoringWeights.builder()
                    .session(session)
                    .technicalSkills(request.getWeights().getTechnicalSkills())
                    .experience(request.getWeights().getExperience())
                    .education(request.getWeights().getEducation())
                    .softSkills(request.getWeights().getSoftSkills())
                    .cultureFit(request.getWeights().getCultureFit())
                    .build();
            // set both sides so cascade persists weights and the response includes them
            session.setWeights(weights);
        }

        return sessionRepository.save(session);
    }

    @Transactional
    public ScreeningSession updateSession(String id, CreateSessionRequest request, String userEmail) {
        if (request.getWeights() != null) requireWeightsTotal100(request.getWeights());
        ScreeningSession session = getSession(id, userEmail);
        
        if (request.getTitle() != null) session.setTitle(request.getTitle());
        if (request.getDescription() != null) session.setDescription(request.getDescription());
        
        if (request.getWeights() != null) {
            ScoringWeights weights = weightsRepository.findBySessionId(session.getId());
            if (weights == null) {
                weights = new ScoringWeights();
                weights.setSession(session);
            }
            weights.setTechnicalSkills(request.getWeights().getTechnicalSkills());
            weights.setExperience(request.getWeights().getExperience());
            weights.setEducation(request.getWeights().getEducation());
            weights.setSoftSkills(request.getWeights().getSoftSkills());
            weights.setCultureFit(request.getWeights().getCultureFit());
            weightsRepository.save(weights);
        }
        
        return sessionRepository.save(session);
    }

    public Page<ScreeningSession> getSessions(String userEmail, Pageable pageable) {
        User user = userRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> ApiException.notFound("User not found",
                        "No user exists for email " + userEmail));
        return sessionRepository.findByUserId(user.getId(), pageable);
    }

    public ScreeningSession getSession(String id, String userEmail) {
        User user = userRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> ApiException.notFound("User not found",
                        "No user exists for email " + userEmail));
        
        return sessionRepository.findWithWeightsById(id)
                .filter(s -> user.getRole().name().equals("ADMIN") || s.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new AccessDeniedException("Not authorized or session not found"));
    }

    @Transactional
    public void deleteSession(String id, String userEmail) {
        ScreeningSession session = getSession(id, userEmail);
        if (session.getStatus() == SessionStatus.RUNNING) {
            throw ApiException.validation("Session is currently running. Cancel it first, then delete.",
                    "Session " + id + " is in status RUNNING");
        }
        minioUploadService.deleteSessionFiles(id);
        sessionRepository.delete(session);
    }

    @Transactional
    public void cancelSession(String id, String userEmail) {
        ScreeningSession session = getSession(id, userEmail);
        if (session.getStatus() != SessionStatus.RUNNING) {
            throw ApiException.validation("Session is not running",
                    "Session " + id + " is in status " + session.getStatus());
        }
        session.setStatus(SessionStatus.FAILED);
        session.setLastErrorMessage("Cancelled by user");
        session.setCompletedAt(java.time.Instant.now());
        sessionRepository.save(session);
    }
}
