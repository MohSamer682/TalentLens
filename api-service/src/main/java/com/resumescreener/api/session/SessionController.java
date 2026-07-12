package com.resumescreener.api.session;

import com.resumescreener.common.dto.WeightsDto;
import com.resumescreener.common.entity.*;
import com.resumescreener.common.enums.SessionStatus;
import com.resumescreener.common.repository.*;
import com.resumescreener.api.session.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final CandidateRepository candidateRepository;

    @PostMapping
    public ResponseEntity<SessionResponse> createSession(@jakarta.validation.Valid @RequestBody CreateSessionRequest request, Authentication auth) {
        ScreeningSession session = sessionService.createSession(request, auth.getName());
        return ResponseEntity.ok(mapToResponse(session));
    }

    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<Page<SessionResponse>> listSessions(Pageable pageable, Authentication auth) {
        return ResponseEntity.ok(sessionService.getSessions(auth.getName(), pageable).map(this::mapToResponse));
    }

    @GetMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<SessionResponse> getSession(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(mapToResponse(sessionService.getSession(id, auth.getName())));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SessionResponse> updateSession(@PathVariable String id, @RequestBody CreateSessionRequest request, Authentication auth) {
        return ResponseEntity.ok(mapToResponse(sessionService.updateSession(id, request, auth.getName())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable String id, Authentication auth) {
        sessionService.deleteSession(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelSession(@PathVariable String id, Authentication auth) {
        sessionService.cancelSession(id, auth.getName());
        return ResponseEntity.noContent().build();
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
