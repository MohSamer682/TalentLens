package com.resumescreener.api.batch;

import com.resumescreener.common.dto.CandidateResultDto;
import com.resumescreener.common.dto.CandidateScoreDetailDto;
import com.resumescreener.api.batch.BatchTriggerService;
import com.resumescreener.api.batch.dto.ProgressResponse;
import com.resumescreener.api.session.SessionService;
import com.resumescreener.api.upload.MinioUploadService;
import com.resumescreener.common.dto.WeightsDto;
import com.resumescreener.common.entity.*;
import com.resumescreener.common.enums.CandidateStatus;
import com.resumescreener.common.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class BatchController {

    private final BatchTriggerService batchTriggerService;
    private final SessionService sessionService;
    private final CandidateRepository candidateRepository;
    private final CandidateScoreRepository candidateScoreRepository;
    private final MinioUploadService minioUploadService;

    @PostMapping("/{id}/start")
    public ResponseEntity<Void> startBatch(@PathVariable String id, Authentication auth) {
        sessionService.getSession(id, auth.getName());
        batchTriggerService.triggerBatch(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/progress")
    public ResponseEntity<ProgressResponse> getProgress(@PathVariable String id, Authentication auth) {
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

        return ResponseEntity.ok(progress);
    }

    @GetMapping("/{id}/candidates")
    public ResponseEntity<org.springframework.data.domain.Page<CandidateResultDto>> getCandidates(
            @PathVariable String id, 
            org.springframework.data.domain.Pageable pageable,
            Authentication auth) {
        sessionService.getSession(id, auth.getName());

        org.springframework.data.domain.Page<CandidateScore> scores =
                candidateScoreRepository.findByCandidateSessionIdOrderByRankAsc(id, pageable);

        org.springframework.data.domain.Page<CandidateResultDto> dtos = scores.map(this::toCandidateResultDto);
        
        return ResponseEntity.ok(dtos);
    }

    private CandidateResultDto toCandidateResultDto(CandidateScore score) {
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
                valueOrZero(score.getCompositeScore())
        );
    }

    private double valueOrZero(Double value) {
        return value != null ? value : 0.0;
    }

    @GetMapping("/{sessionId}/candidates/{candidateId}")
    public ResponseEntity<CandidateScoreDetailDto> getCandidateDetail(
            @PathVariable String sessionId,
            @PathVariable String candidateId,
            Authentication auth) {
        ScreeningSession session = sessionService.getSession(sessionId, auth.getName());
        CandidateScore score = candidateScoreRepository.findByCandidateId(candidateId).stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Score not found"));

        Candidate c = score.getCandidate();
        
        WeightsDto weightsDto = null;
        if (session.getWeights() != null) {
            weightsDto = new WeightsDto();
            weightsDto.setTechnicalSkills(session.getWeights().getTechnicalSkills());
            weightsDto.setExperience(session.getWeights().getExperience());
            weightsDto.setEducation(session.getWeights().getEducation());
            weightsDto.setSoftSkills(session.getWeights().getSoftSkills());
            weightsDto.setCultureFit(session.getWeights().getCultureFit());
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
                weightsDto
        );

        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<Map<String, String>> export(@PathVariable String id, Authentication auth) {
        ScreeningSession session = sessionService.getSession(id, auth.getName());
        
        if (session.getReportFileKey() == null) {
            throw new RuntimeException("Report not generated yet");
        }

        String url = minioUploadService.getPresignedUrl(session.getReportFileKey());
        return ResponseEntity.ok(Map.of("url", url));
    }
}
