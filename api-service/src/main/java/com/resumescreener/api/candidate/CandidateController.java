package com.resumescreener.api.candidate;

import com.resumescreener.api.candidate.dto.ResumeFileDto;
import com.resumescreener.api.common.ApiResponse;
import com.resumescreener.common.session.CandidateStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
}
