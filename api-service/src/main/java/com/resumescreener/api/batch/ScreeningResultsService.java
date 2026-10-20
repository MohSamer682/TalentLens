package com.resumescreener.api.batch;

import com.resumescreener.api.audit.AuditService;
import com.resumescreener.api.batch.dto.ExportResponse;
import com.resumescreener.api.batch.dto.ProgressResponse;
import com.resumescreener.api.batch.dto.ResumeDownloadResponse;
import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.session.SessionService;
import com.resumescreener.api.upload.MinioUploadService;
import com.resumescreener.common.session.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What a screening produced: progress, ranked candidates, one candidate's scores, resume and report links. */
@Service
@RequiredArgsConstructor
public class ScreeningResultsService {

    private static final int PRESIGNED_URL_MINUTES = 15;

    private final SessionService sessionService;
    private final CandidateRepository candidateRepository;
    private final CandidateScoreRepository candidateScoreRepository;
    private final MinioUploadService minioUploadService;
    private final AuditService auditService;

    public ProgressResponse getProgress(String id, String email) {
        ScreeningSession session = sessionService.getSession(id, email);

        long processed = candidateRepository.countBySessionIdAndStatus(id, CandidateStatus.SCORED);
        long failed = candidateRepository.countBySessionIdAndStatus(id, CandidateStatus.FAILED);
        long total = candidateRepository.countBySessionId(id);
        int percent = total > 0 ? (int) ((processed + failed) * 100 / total) : 0;

        ProgressResponse progress = new ProgressResponse();
        progress.setTotal((int) total);
        progress.setProcessed((int) processed);
        progress.setFailed((int) failed);
        progress.setStatus(session.getStatus().name());
        progress.setPercent(percent);
        return progress;
    }

    @Transactional(readOnly = true)
    public Page<CandidateResultDto> getCandidates(String id, ReviewStatus review, Pageable pageable, String email) {
        sessionService.getSession(id, email);

        // review=SHORTLISTED means shortlisted or any later stage, so a candidate who moves on stays in the view
        Page<CandidateScore> scores = review == null
                ? candidateScoreRepository.findByCandidateSessionIdOrderByRankAsc(id, pageable)
                : review == ReviewStatus.SHORTLISTED
                ? candidateScoreRepository.findByCandidateSessionIdAndCandidateReviewStatusNotOrderByRankAsc(id, ReviewStatus.NONE, pageable)
                : candidateScoreRepository.findByCandidateSessionIdAndCandidateReviewStatusOrderByRankAsc(id, review, pageable);
        return scores.map(ScreeningResultsService::toCandidateResultDto);
    }

    @Transactional(readOnly = true)
    public CandidateScoreDetailDto getCandidateDetail(String sessionId, String candidateId, String email) {
        ScreeningSession session = sessionService.getSession(sessionId, email);
        CandidateScore score = candidateScoreRepository.findByCandidateId(candidateId).stream()
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Candidate score not found",
                        "No score exists for candidate " + candidateId));

        Candidate c = score.getCandidate();
        // The session check above only proves the caller owns sessionId, not that this candidate belongs to it
        if (c.getSession() == null || !sessionId.equals(c.getSession().getId())) {
            throw ApiException.notFound("Candidate score not found",
                    "No score exists for candidate " + candidateId);
        }

        return new CandidateScoreDetailDto(
                score.getId(),
                c.getId(),
                score.getTechnicalSkills(),
                score.getExperience(),
                score.getEducation(),
                score.getSoftSkills(),
                score.getCultureFit(),
                score.getCompositeScore(),
                score.getRank(),
                score.getSkills(),
                score.getJustification(),
                score.getStrengths(),
                score.getGaps(),
                c.getName(),
                c.getEmail(),
                toWeightsDto(session.getWeights()),
                c.getReviewStatus()
        );
    }

    /**
     * The resume itself stays in object storage; the client receives a short-lived presigned URL
     * rather than the API streaming bytes through.
     */
    public ResumeDownloadResponse getCandidateResume(String sessionId, String candidateId, String email) {
        sessionService.getSession(sessionId, email);

        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> ApiException.notFound("Candidate not found",
                        "No candidate exists for id " + candidateId));

        if (candidate.getSession() == null || !sessionId.equals(candidate.getSession().getId())) {
            throw ApiException.notFound("Candidate not found in this session",
                    "Candidate " + candidateId + " does not belong to session " + sessionId);
        }
        if (candidate.getResumeFileKey() == null) {
            throw ApiException.notFound("Resume file is missing",
                    "Candidate " + candidateId + " has no stored resume key");
        }

        String url = minioUploadService.getPresignedUrl(candidate.getResumeFileKey());
        auditService.record("RESUME_DOWNLOAD", "candidate", candidateId, "success");
        return new ResumeDownloadResponse(candidateId, fileNameOf(candidate), url, PRESIGNED_URL_MINUTES);
    }

    public ExportResponse getExport(String id, String email) {
        ScreeningSession session = sessionService.getSession(id, email);

        if (session.getReportFileKey() == null) {
            throw ApiException.validation("Report not generated yet",
                    "Session " + id + " has no report file; run the screening to completion first");
        }

        String url = minioUploadService.getPresignedUrl(session.getReportFileKey());
        auditService.record("EXPORT", "session", id, "success");
        return new ExportResponse(id, url, PRESIGNED_URL_MINUTES);
    }

    private static CandidateResultDto toCandidateResultDto(CandidateScore score) {
        Candidate candidate = score.getCandidate();
        return new CandidateResultDto(
                candidate.getId(),
                score.getRank() != null ? score.getRank() : 0,
                candidate.getName(),
                candidate.getEmail(),
                valueOrZero(score.getTechnicalSkills()),
                valueOrZero(score.getExperience()),
                valueOrZero(score.getEducation()),
                valueOrZero(score.getSoftSkills()),
                valueOrZero(score.getCultureFit()),
                valueOrZero(score.getCompositeScore()),
                candidate.getReviewStatus()
        );
    }

    private static WeightsDto toWeightsDto(ScoringWeights weights) {
        if (weights == null) {
            return null;
        }
        WeightsDto dto = new WeightsDto();
        dto.setTechnicalSkills(weights.getTechnicalSkills());
        dto.setExperience(weights.getExperience());
        dto.setEducation(weights.getEducation());
        dto.setSoftSkills(weights.getSoftSkills());
        dto.setCultureFit(weights.getCultureFit());
        return dto;
    }

    private static String fileNameOf(Candidate candidate) {
        String name = candidate.getName();
        if (name == null || name.isBlank()) {
            return "resume.pdf";
        }
        return name.trim().replaceAll("[^A-Za-z0-9 ._-]", "").replace(' ', '_') + ".pdf";
    }

    private static double valueOrZero(Double value) {
        return value != null ? value : 0.0;
    }
}
