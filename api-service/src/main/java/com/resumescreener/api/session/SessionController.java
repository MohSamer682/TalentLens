package com.resumescreener.api.session;

import com.resumescreener.api.batch.dto.SessionActionResponse;
import com.resumescreener.api.common.ApiResponse;
import com.resumescreener.api.session.dto.*;
import com.resumescreener.common.dto.WeightsDto;
import com.resumescreener.common.entity.*;
import com.resumescreener.common.enums.SessionStatus;
import com.resumescreener.common.repository.*;
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
public class SessionController {

    private final SessionService sessionService;
    private final CandidateRepository candidateRepository;

    @PostMapping
    public ResponseEntity<ApiResponse<SessionResponse>> createSession(
            @jakarta.validation.Valid @RequestBody CreateSessionRequest request, Authentication auth) {
        ScreeningSession session = sessionService.createSession(request, auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Session created", mapToResponse(session)));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Page<SessionResponse>>> listSessions(
            Pageable pageable, Authentication auth) {
        Page<SessionResponse> data = sessionService.getSessions(auth.getName(), pageable)
                .map(this::mapToResponse);
        return ResponseEntity.ok(ApiResponse.success("Sessions loaded", data));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<SessionResponse>> getSession(
            @PathVariable String id, Authentication auth) {
        SessionResponse data = mapToResponse(sessionService.getSession(id, auth.getName()));
        return ResponseEntity.ok(ApiResponse.success("Session loaded", data));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<SessionResponse>> updateSession(
            @PathVariable String id, @RequestBody CreateSessionRequest request, Authentication auth) {
        SessionResponse data = mapToResponse(sessionService.updateSession(id, request, auth.getName()));
        return ResponseEntity.ok(ApiResponse.success("Session updated", data));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<SessionActionResponse>> deleteSession(
            @PathVariable String id, Authentication auth) {
        sessionService.deleteSession(id, auth.getName());
        // The row is gone, so there is no status left to report — only the id and the outcome
        SessionActionResponse data = new SessionActionResponse(id, null, "Session deleted");
        return ResponseEntity.ok(ApiResponse.success("Session deleted", data));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<SessionActionResponse>> cancelSession(
            @PathVariable String id, Authentication auth) {
        sessionService.cancelSession(id, auth.getName());
        SessionActionResponse data =
                new SessionActionResponse(id, SessionStatus.FAILED, "Cancelled by user");
        return ResponseEntity.ok(ApiResponse.success("Screening cancelled", data));
    }

    private SessionResponse mapToResponse(ScreeningSession session) {
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
        return new SessionResponse(session.getId(), session.getTitle(), session.getDescription(),
                session.getStatus(), session.getJdFileKey(), session.getCreatedAt(), session.getCompletedAt(),
                weightsDto, count, session.getLastErrorMessage(), session.getReportFileKey());
    }
}
