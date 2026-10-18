package com.resumescreener.api.candidate;

import com.resumescreener.api.candidate.dto.BulkReviewRequest;
import com.resumescreener.api.candidate.dto.CandidateNoteDto;
import com.resumescreener.api.candidate.dto.CreateNoteRequest;
import com.resumescreener.api.candidate.dto.ResumeFileDto;
import com.resumescreener.api.candidate.dto.ReviewRequest;
import com.resumescreener.api.candidate.dto.ReviewResult;
import com.resumescreener.api.candidate.dto.TopShortlistRequest;
import jakarta.validation.Valid;
import java.util.List;
import com.resumescreener.api.common.ApiResponse;
import com.resumescreener.common.session.CandidateStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class CandidateController {

    private final CandidateService candidateService;

    @GetMapping("/{id}/resumes")
    public ResponseEntity<ApiResponse<Page<ResumeFileDto>>> listResumes(
            @PathVariable String id,
            @RequestParam(required = false) CandidateStatus status,
            @PageableDefault(size = 50) Pageable pageable,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Resumes loaded",
                candidateService.listResumes(id, status, pageable, auth.getName())));
    }

    @PatchMapping("/{id}/candidates/{candidateId}/review")
    @PreAuthorize("hasAuthority('CANDIDATE_REVIEW')")
    public ResponseEntity<ApiResponse<ReviewResult>> review(
            @PathVariable String id,
            @PathVariable String candidateId,
            @Valid @RequestBody ReviewRequest request,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Review saved",
                candidateService.review(id, List.of(candidateId), request.status(), auth.getName())));
    }

    @PostMapping("/{id}/candidates/review")
    @PreAuthorize("hasAuthority('CANDIDATE_REVIEW')")
    public ResponseEntity<ApiResponse<ReviewResult>> reviewMany(
            @PathVariable String id,
            @Valid @RequestBody BulkReviewRequest request,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Reviews saved",
                candidateService.review(id, request.candidateIds(), request.status(), auth.getName())));
    }

    @PostMapping("/{id}/shortlist/top")
    @PreAuthorize("hasAuthority('CANDIDATE_REVIEW')")
    public ResponseEntity<ApiResponse<ReviewResult>> shortlistTop(
            @PathVariable String id,
            @Valid @RequestBody TopShortlistRequest request,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Shortlist saved",
                candidateService.shortlistTop(id, request.count(), auth.getName())));
    }

    @GetMapping("/{id}/candidates/{candidateId}/notes")
    public ResponseEntity<ApiResponse<List<CandidateNoteDto>>> listNotes(
            @PathVariable String id,
            @PathVariable String candidateId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Notes loaded",
                candidateService.listNotes(id, candidateId, auth.getName())));
    }

    @PostMapping("/{id}/candidates/{candidateId}/notes")
    public ResponseEntity<ApiResponse<CandidateNoteDto>> addNote(
            @PathVariable String id,
            @PathVariable String candidateId,
            @Valid @RequestBody CreateNoteRequest request,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Note added",
                candidateService.addNote(id, candidateId, request.content(), auth.getName())));
    }

    @DeleteMapping("/{id}/candidates/{candidateId}/notes/{noteId}")
    public ResponseEntity<ApiResponse<Void>> deleteNote(
            @PathVariable String id,
            @PathVariable String candidateId,
            @PathVariable String noteId,
            Authentication auth) {
        candidateService.deleteNote(id, candidateId, noteId, auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Note deleted", null));
    }
}
