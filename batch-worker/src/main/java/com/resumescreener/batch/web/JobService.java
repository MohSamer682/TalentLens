package com.resumescreener.batch.web;

import com.resumescreener.batch.excel.ExcelReportGenerator;
import com.resumescreener.batch.storage.MinioStorageService;
import com.resumescreener.common.dto.LlmScoreDto;
import com.resumescreener.common.entity.*;
import com.resumescreener.common.enums.CandidateStatus;
import com.resumescreener.common.enums.SessionStatus;
import com.resumescreener.common.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    private final ScreeningSessionRepository sessionRepository;
    private final CandidateRepository candidateRepository;
    private final CandidateScoreRepository candidateScoreRepository;
    private final ScoringWeightsRepository weightsRepository;
    private final BatchJobRepository batchJobRepository;
    private final MinioStorageService minioStorageService;
    private final ExcelReportGenerator excelReportGenerator;
    private final CandidateProcessingService candidateProcessingService;

    @Async
    public void processCandidates(String sessionId) {
        log.info("Starting background processing for session: {}", sessionId);
        
        ScreeningSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) return;

        List<Candidate> candidates = candidateRepository.findBySessionId(sessionId);
        ScoringWeights weights = weightsRepository.findBySessionId(sessionId);

        BatchJob job = BatchJob.builder()
                .session(session)
                .total(candidates.size())
                .processed(0)
                .failed(0)
                .startedAt(java.time.Instant.now())
                .build();
        job = batchJobRepository.save(job);
        
        if (weights == null) {
            log.warn("No weights found for session {}, using defaults", sessionId);
            weights = ScoringWeights.builder()
                    .technicalSkills(30.0)
                    .experience(25.0)
                    .education(15.0)
                    .softSkills(20.0)
                    .cultureFit(10.0)
                    .build();
        }
        
        // Extract JD once
        String jdText = "";
        try {
            if (session.getJdFileKey() != null) {
                // We'll use the candidateProcessingService's extractor for now or just move this logic to a utility
                // For a quick fix, I will add PdfTextExtractor back to JobService or just call it from candidateProcessingService
                byte[] jdBytes = minioStorageService.downloadBytes(session.getJdFileKey());
                jdText = candidateProcessingService.getPdfTextExtractor().extract(jdBytes);
            } else {
                log.warn("No JD file key for session {}", sessionId);
                jdText = "General job description for recruitment";
            }
        } catch (Exception e) {
            log.error("Failed to extract JD for session {}: {}", sessionId, e.getMessage());
            updateSessionStatus(sessionId, SessionStatus.FAILED, "Failed to extract JD: " + e.getMessage());
            return;
        }

        for (Candidate candidate : candidates) {
            try {
                candidateProcessingService.processSingleCandidate(candidate, jdText, weights, job);
            } catch (Exception e) {
                log.error("Failed to process candidate {}: {}", candidate.getId(), e.getMessage());
                candidateProcessingService.handleCandidateFailure(candidate, job);
                updateSessionErrorMessage(sessionId, e.getMessage());
            }
        }

        try {
            rankCandidates(sessionId);
            generateExcelReport(sessionId);
            finalizeSession(sessionId, job);
        } catch (Exception e) {
            log.error("Error finalizing session {}: {}", sessionId, e.getMessage());
            updateSessionStatus(sessionId, SessionStatus.FAILED, "Finalization failed: " + e.getMessage());
        }
        
        log.info("Completed processing for session: {}", sessionId);
    }

    @Transactional
    public void updateSessionStatus(String sessionId, SessionStatus status, String error) {
        ScreeningSession session = sessionRepository.findById(sessionId).orElseThrow();
        session.setStatus(status);
        if (error != null) session.setLastErrorMessage(error);
        session.setCompletedAt(java.time.Instant.now());
        sessionRepository.save(session);
    }

    @Transactional
    public void updateSessionErrorMessage(String sessionId, String error) {
        ScreeningSession session = sessionRepository.findById(sessionId).orElseThrow();
        session.setLastErrorMessage(error);
        sessionRepository.save(session);
    }

    @Transactional
    public void rankCandidates(String sessionId) {
        List<CandidateScore> scores = candidateScoreRepository.findByCandidateSessionIdOrderByRankAsc(sessionId);
        scores.sort((a, b) -> Double.compare(b.getCompositeScore(), a.getCompositeScore()));
        int rank = 1;
        for (CandidateScore score : scores) {
            score.setRank(rank++);
        }
        candidateScoreRepository.saveAll(scores);
    }

    @Transactional
    public void generateExcelReport(String sessionId) throws Exception {
        ScreeningSession session = sessionRepository.findById(sessionId).orElseThrow();
        List<CandidateScore> scores = candidateScoreRepository.findByCandidateSessionIdOrderByRankAsc(sessionId);
        ScoringWeights weights = weightsRepository.findBySessionId(sessionId);
        byte[] excel = excelReportGenerator.generate(scores, weights);
        String key = "reports/" + session.getId() + "/" + UUID.randomUUID() + ".xlsx";
        minioStorageService.uploadBytes(key, excel, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        session.setReportFileKey(key);
        sessionRepository.save(session);
    }

    @Transactional
    public void finalizeSession(String sessionId, BatchJob job) {
        ScreeningSession session = sessionRepository.findById(sessionId).orElseThrow();
        BatchJob currentJob = batchJobRepository.findById(job.getId()).orElse(job);
        currentJob.setFinishedAt(java.time.Instant.now());
        batchJobRepository.save(currentJob);

        if (session.getLastErrorMessage() != null && currentJob.getProcessed() == 0) {
            session.setStatus(SessionStatus.FAILED);
        } else {
            session.setStatus(SessionStatus.DONE);
        }
        session.setCompletedAt(java.time.Instant.now());
        sessionRepository.save(session);
    }
}
