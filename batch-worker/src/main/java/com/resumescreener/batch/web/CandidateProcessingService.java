package com.resumescreener.batch.web;

import com.resumescreener.batch.llm.LlmClient;
import com.resumescreener.common.dto.LlmScoreDto;
import com.resumescreener.batch.pdf.PdfTextExtractor;
import com.resumescreener.batch.storage.MinioStorageService;
import com.resumescreener.common.entity.*;
import com.resumescreener.common.enums.CandidateStatus;
import com.resumescreener.common.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.Getter;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Getter
public class CandidateProcessingService {

    private final CandidateRepository candidateRepository;
    private final CandidateScoreRepository candidateScoreRepository;
    private final BatchJobRepository batchJobRepository;
    private final MinioStorageService minioStorageService;
    private final PdfTextExtractor pdfTextExtractor;
    private final LlmClient llmClient;

    @Transactional
    public void processSingleCandidate(Candidate candidate, String jdText, ScoringWeights weights, BatchJob job) throws Exception {
        MDC.put("candidateId", candidate.getId());
        log.info("Processing candidate");
        byte[] pdfBytes = minioStorageService.downloadBytes(candidate.getResumeFileKey());
        String text = pdfTextExtractor.extract(pdfBytes);
        candidate.setRawText(text);

        LlmScoreDto score = llmClient.score(jdText, text);
        
        double composite = (score.getTechnicalSkills() * weights.getTechnicalSkills() +
                           score.getExperience() * weights.getExperience() +
                           score.getEducation() * weights.getEducation() +
                           score.getSoftSkills() * weights.getSoftSkills() +
                           score.getCultureFit() * weights.getCultureFit()) / 100.0;

        CandidateScore candidateScore = CandidateScore.builder()
                .candidate(candidate)
                .technicalSkills((double) score.getTechnicalSkills())
                .experience((double) score.getExperience())
                .education((double) score.getEducation())
                .softSkills((double) score.getSoftSkills())
                .cultureFit((double) score.getCultureFit())
                .compositeScore(composite)
                .skills(score.getSkills())
                .justification(score.getJustification())
                .strengths(score.getStrengths())
                .gaps(score.getGaps())
                .build();
        
        candidateScoreRepository.save(candidateScore);
        candidate.setStatus(CandidateStatus.SCORED);
        candidateRepository.save(candidate);

        BatchJob currentJob = batchJobRepository.findById(job.getId()).orElse(job);
        currentJob.setProcessed(currentJob.getProcessed() + 1);
        batchJobRepository.save(currentJob);
        log.info("Candidate scored, composite={}", String.format("%.2f", composite));
        MDC.remove("candidateId");
    }

    @Transactional
    public void handleCandidateFailure(Candidate candidate, BatchJob job) {
        candidate.setStatus(CandidateStatus.FAILED);
        candidateRepository.save(candidate);

        BatchJob currentJob = batchJobRepository.findById(job.getId()).orElse(job);
        currentJob.setFailed(currentJob.getFailed() + 1);
        batchJobRepository.save(currentJob);
    }
}
