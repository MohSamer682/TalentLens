package com.resumescreener.api.batch;

import com.resumescreener.api.batch.dto.ExportResponse;
import com.resumescreener.api.batch.dto.ProgressResponse;
import com.resumescreener.api.batch.dto.ResumeDownloadResponse;
import com.resumescreener.api.session.dto.SessionActionResponse;
import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.common.ApiResponse;
import com.resumescreener.api.session.SessionService;
import com.resumescreener.api.upload.MinioUploadService;
import com.resumescreener.common.session.CandidateResultDto;
import com.resumescreener.common.session.CandidateScoreDetailDto;
import com.resumescreener.common.session.WeightsDto;
import com.resumescreener.common.audit.*;
import com.resumescreener.common.session.*;
import com.resumescreener.common.user.*;
import com.resumescreener.common.session.CandidateStatus;
import com.resumescreener.common.session.SessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class BatchController {

    private static final int PRESIGNED_URL_MINUTES = 15;

    private final BatchTriggerService batchTriggerService;
    private final SessionService sessionService;
    private final CandidateRepository candidateRepository;
    private final CandidateScoreRepository candidateScoreRepository;
    private final MinioUploadService minioUploadService;
    private final com.resumescreener.api.audit.AuditService auditService;

    @PostMapping("/{id}/start")
    public ResponseEntity<ApiResponse<SessionActionResponse>> startBatch(
            @PathVariable String id,
            @RequestParam(defaultValue = "UNSCORED") ScoringScope scope,
            Authentication auth) {
        sessionService.getSession(id, auth.getName());
        batchTriggerService.triggerBatch(id, scope);
        auditService.record("BATCH_TRIGGER", "session", id, "success", "scope " + scope);

        SessionActionResponse data =
                new SessionActionResponse(id, SessionStatus.RUNNING, "Screening started");
        return ResponseEntity.ok(ApiResponse.success("Screening started", data));
    }

    @GetMapping("/{id}/progress")
    public ResponseEntity<ApiResponse<ProgressResponse>> getProgress(
            @PathVariable String id, Authentication auth) {
        ScreeningSession session = sessionService.getSession(id, auth.getName());

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

        return ResponseEntity.ok(ApiResponse.success("Progress loaded", progress));
    }

    @GetMapping("/{id}/candidates")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Page<CandidateResultDto>>> getCandidates(
            @PathVariable String id,
            @RequestParam(required = false) ReviewStatus review,
            Pageable pageable,
            Authentication auth) {
        sessionService.getSession(id, auth.getName());

        Page<CandidateScore> scores = review == null
                ? candidateScoreRepository.findByCandidateSessionIdOrderByRankAsc(id, pageable)
                : candidateScoreRepository.findByCandidateSessionIdAndCandidateReviewStatusOrderByRankAsc(id, review, pageable);
        Page<CandidateResultDto> dtos = scores.map(BatchController::toCandidateResultDto);

        return ResponseEntity.ok(ApiResponse.success("Candidates loaded", dtos));
    }

    @GetMapping("/{sessionId}/candidates/{candidateId}")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<CandidateScoreDetailDto>> getCandidateDetail(
            @PathVariable String sessionId,
            @PathVariable String candidateId,
            Authentication auth) {
        ScreeningSession session = sessionService.getSession(sessionId, auth.getName());
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

        CandidateScoreDetailDto dto = new CandidateScoreDetailDto(
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

        return ResponseEntity.ok(ApiResponse.success("Candidate loaded", dto));
    }

    /**
     * The resume itself stays in object storage; the client receives a short-lived presigned URL
     * rather than the API streaming bytes through.
     */
    @GetMapping("/{sessionId}/candidates/{candidateId}/resume")
    public ResponseEntity<ApiResponse<ResumeDownloadResponse>> getCandidateResume(
            @PathVariable String sessionId,
            @PathVariable String candidateId,
            Authentication auth) {
        sessionService.getSession(sessionId, auth.getName());

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
        ResumeDownloadResponse data = new ResumeDownloadResponse(
                candidateId, fileNameOf(candidate), url, PRESIGNED_URL_MINUTES);

        return ResponseEntity.ok(ApiResponse.success("Resume download link generated", data));
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<ApiResponse<ExportResponse>> export(
            @PathVariable String id, Authentication auth) {
        ScreeningSession session = sessionService.getSession(id, auth.getName());

        if (session.getReportFileKey() == null) {
            throw ApiException.validation("Report not generated yet",
                    "Session " + id + " has no report file; run the screening to completion first");
        }

        String url = minioUploadService.getPresignedUrl(session.getReportFileKey());
        auditService.record("EXPORT", "session", id, "success");
        ExportResponse data = new ExportResponse(id, url, PRESIGNED_URL_MINUTES);
        return ResponseEntity.ok(ApiResponse.success("Export URL generated", data));
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
