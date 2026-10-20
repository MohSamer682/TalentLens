package com.resumescreener.api.batch;

import com.resumescreener.api.audit.AuditService;
import com.resumescreener.api.batch.dto.ExportResponse;
import com.resumescreener.api.batch.dto.ProgressResponse;
import com.resumescreener.api.batch.dto.ResumeDownloadResponse;
import com.resumescreener.api.common.ApiResponse;
import com.resumescreener.api.session.SessionService;
import com.resumescreener.api.session.dto.SessionActionResponse;
import com.resumescreener.common.session.CandidateResultDto;
import com.resumescreener.common.session.CandidateScoreDetailDto;
import com.resumescreener.common.session.ReviewStatus;
import com.resumescreener.common.session.ScoringScope;
import com.resumescreener.common.session.SessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class BatchController {

    private final BatchTriggerService batchTriggerService;
    private final SessionService sessionService;
    private final ScreeningResultsService results;
    private final AuditService auditService;

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('SESSION_RUN')")
    public ResponseEntity<ApiResponse<SessionActionResponse>> startBatch(
            @PathVariable String id,
            @RequestParam(defaultValue = "UNSCORED") ScoringScope scope,
            Authentication auth) {
        sessionService.getOwnedSession(id, auth.getName());
        batchTriggerService.triggerBatch(id, scope);
        auditService.record("BATCH_TRIGGER", "session", id, "success", "scope " + scope);

        SessionActionResponse data =
                new SessionActionResponse(id, SessionStatus.RUNNING, "Screening started");
        return ResponseEntity.ok(ApiResponse.success("Screening started", data));
    }

    @GetMapping("/{id}/progress")
    public ResponseEntity<ApiResponse<ProgressResponse>> getProgress(
            @PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Progress loaded", results.getProgress(id, auth.getName())));
    }

    @GetMapping("/{id}/candidates")
    public ResponseEntity<ApiResponse<Page<CandidateResultDto>>> getCandidates(
            @PathVariable String id,
            @RequestParam(required = false) ReviewStatus review,
            Pageable pageable,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Candidates loaded",
                results.getCandidates(id, review, pageable, auth.getName())));
    }

    @GetMapping("/{sessionId}/candidates/{candidateId}")
    public ResponseEntity<ApiResponse<CandidateScoreDetailDto>> getCandidateDetail(
            @PathVariable String sessionId,
            @PathVariable String candidateId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Candidate loaded",
                results.getCandidateDetail(sessionId, candidateId, auth.getName())));
    }

    @GetMapping("/{sessionId}/candidates/{candidateId}/resume")
    public ResponseEntity<ApiResponse<ResumeDownloadResponse>> getCandidateResume(
            @PathVariable String sessionId,
            @PathVariable String candidateId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Resume download link generated",
                results.getCandidateResume(sessionId, candidateId, auth.getName())));
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<ApiResponse<ExportResponse>> export(
            @PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Export URL generated", results.getExport(id, auth.getName())));
    }
}
