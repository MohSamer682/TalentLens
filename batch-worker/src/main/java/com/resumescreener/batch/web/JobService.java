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
import org.slf4j.MDC;
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
        MDC.put("sessionId", sessionId);
        log.info("Starting batch processing for session with candidates");
        
        ScreeningSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            log.warn("Session not found, aborting");
            return;
        }

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
        
        log.info("Batch job created, total={}", candidates.size());

        if (weights == null) {
            log.warn("No weights found, using defaults");
            weights = ScoringWeights.builder()
                    .technicalSkills(30.0)
                    .experience(25.0)
                    .education(15.0)
                    .softSkills(20.0)
                    .cultureFit(10.0)
                    .build();
        }
        
        String jdText = "";
        try {
            if (session.getJdFileKey() != null) {
                byte[] jdBytes = minioStorageService.downloadBytes(session.getJdFileKey());
                jdText = candidateProcessingService.getPdfTextExtractor().extract(jdBytes);
            } else {
                log.warn("No JD file key, using generic description");
                jdText = "General job description for recruitment";
            }
        } catch (Exception e) {
            log.error("Failed to extract JD", e);
            updateSessionStatus(sessionId, SessionStatus.FAILED, "Failed to extract JD: " + e.getMessage());
            return;
        }

        for (Candidate candidate : candidates) {
            try {
                candidateProcessingService.processSingleCandidate(candidate, jdText, weights, job);
            } catch (Exception e) {
                log.error("Failed to process candidate {}", candidate.getId(), e);
                candidateProcessingService.handleCandidateFailure(candidate, job);
                updateSessionErrorMessage(sessionId, e.getMessage());
            }
        }

        try {
            rankCandidates(sessionId);
            generateExcelReport(sessionId);
            finalizeSession(sessionId, job);
            log.info("Batch completed, processed={}, failed={}", 
                    batchJobRepository.findById(job.getId()).map(BatchJob::getProcessed).orElse(0),
                    batchJobRepository.findById(job.getId()).map(BatchJob::getFailed).orElse(0));
        } catch (Exception e) {
            log.error("Error finalizing session", e);
            updateSessionStatus(sessionId, SessionStatus.FAILED, "Finalization failed: " + e.getMessage());
        }
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
