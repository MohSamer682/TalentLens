package com.resumescreener.api.session;

import com.resumescreener.api.session.dto.SessionActionResponse;
import com.resumescreener.api.llm.LlmStatusClient;
import com.resumescreener.api.common.ApiResponse;
import com.resumescreener.api.session.dto.*;
import com.resumescreener.common.session.WeightsDto;
import com.resumescreener.common.audit.*;
import com.resumescreener.common.session.*;
import com.resumescreener.common.user.*;
import com.resumescreener.common.session.SessionStatus;
import com.resumescreener.api.jobs.JobDetailsMapper;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final CandidateRepository candidateRepository;
    private final LlmStatusClient llmStatusClient;

    @PostMapping
    @PreAuthorize("hasAuthority('SESSION_RUN')")
    public ResponseEntity<ApiResponse<SessionResponse>> createSession(
            @Validated({OnCreate.class, Default.class}) @RequestBody CreateSessionRequest request, Authentication auth) {
        ScreeningSession session = sessionService.createSession(request, auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Session created", mapToResponse(session, true)));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Page<SessionResponse>>> listSessions(
            Pageable pageable, @RequestParam(defaultValue = "false") boolean everyone, Authentication auth) {
        Page<SessionResponse> data = sessionService.getSessions(auth.getName(), everyone, pageable)
                .map(s -> {
                    SessionResponse r = mapToResponse(s, false);
                    if (everyone) r.setOwnerName(s.getUser().getFullName());
                    return r;
                });
        return ResponseEntity.ok(ApiResponse.success("Sessions loaded", data));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<SessionResponse>> getSession(
            @PathVariable String id, Authentication auth) {
        SessionResponse data = mapToResponse(sessionService.getSession(id, auth.getName()), true);
        data.setLlm(llmStatusClient.getStatus());
        return ResponseEntity.ok(ApiResponse.success("Session loaded", data));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('SESSION_RUN')")
    public ResponseEntity<ApiResponse<SessionResponse>> updateSession(
            @PathVariable String id, @Validated @RequestBody CreateSessionRequest request, Authentication auth) {
        SessionResponse data = mapToResponse(sessionService.updateSession(id, request, auth.getName()), true);
        return ResponseEntity.ok(ApiResponse.success("Session updated", data));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SESSION_RUN')")
    public ResponseEntity<ApiResponse<SessionActionResponse>> deleteSession(
            @PathVariable String id, Authentication auth) {
        sessionService.deleteSession(id, auth.getName());
        // The row is gone, so there is no status left to report — only the id and the outcome
        SessionActionResponse data = new SessionActionResponse(id, null, "Session deleted");
        return ResponseEntity.ok(ApiResponse.success("Session deleted", data));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SESSION_RUN')")
    public ResponseEntity<ApiResponse<SessionActionResponse>> cancelSession(
            @PathVariable String id, Authentication auth) {
        sessionService.cancelSession(id, auth.getName());
        SessionActionResponse data =
                new SessionActionResponse(id, SessionStatus.FAILED, "Cancelled by user");
        return ResponseEntity.ok(ApiResponse.success("Screening cancelled", data));
    }

    private SessionResponse mapToResponse(ScreeningSession session, boolean withSkills) {
        var weights = session.getWeights();
        WeightsDto weightsDto = null;
        if (weights != null) {
            weightsDto = new WeightsDto();
            weightsDto.setTechnicalSkills(weights.getTechnicalSkills());
            weightsDto.setExperience(weights.getExperience());
            weightsDto.setEducation(weights.getEducation());
            weightsDto.setSoftSkills(weights.getSoftSkills());
            weightsDto.setCultureFit(weights.getCultureFit());
        }
        int count = (int) candidateRepository.countBySessionId(session.getId());
        SessionResponse response = new SessionResponse(session.getId(), session.getTitle(), session.getDescription(),
                session.getStatus(), session.getJdFileKey(), session.getCreatedAt(), session.getCompletedAt(),
                weightsDto, count, session.getLastErrorMessage(), session.getReportFileKey());
        // getId on the lazy owner reads the foreign key, so this never touches the users table
        if (session.getUser() != null) response.setOwnerId(session.getUser().getId());
        response.setJob(JobDetailsMapper.from(session, withSkills));
        response.setShortlistedCount((int) candidateRepository.countBySessionIdAndReviewStatusNot(
                session.getId(), ReviewStatus.NONE));
        return response;
    }
}
