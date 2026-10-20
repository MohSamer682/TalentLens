package com.resumescreener.batch.web;

import com.resumescreener.batch.llm.LlmClient;
import com.resumescreener.common.llm.LlmScoreDto;
import com.resumescreener.batch.pdf.PdfTextExtractor;
import com.resumescreener.batch.storage.MinioStorageService;
import com.resumescreener.common.audit.*;
import com.resumescreener.common.session.*;
import com.resumescreener.common.user.*;
import com.resumescreener.common.session.CandidateStatus;
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

    static final int MAX_REASON_LENGTH = 500;

    private final CandidateRepository candidateRepository;
    private final CandidateScoreRepository candidateScoreRepository;
    private final BatchJobRepository batchJobRepository;
    private final CandidateTextRepository candidateTextRepository;
    private final MinioStorageService minioStorageService;
    private final PdfTextExtractor pdfTextExtractor;
    private final LlmClient llmClient;

    @Transactional
    public void processSingleCandidate(Candidate detached, String jdText, ScoringWeights weights, BatchJob job) throws Exception {
        // The caller loaded this candidate outside any transaction; work on a managed copy so its lazy
        // associations (session, existing score) can load and the save does not merge a detached graph
        Candidate candidate = candidateRepository.findById(detached.getId()).orElse(detached);
        MDC.put("candidateId", candidate.getId());
        log.info("Processing candidate");
        byte[] pdfBytes = minioStorageService.downloadBytes(candidate.getResumeFileKey());
        String text = pdfTextExtractor.extract(pdfBytes);
        candidateTextRepository.save(CandidateText.builder().candidateId(candidate.getId()).rawText(text).build());

        LlmScoreDto score = llmClient.score(jdText, text);
        
        double composite = (score.getTechnicalSkills() * weights.getTechnicalSkills() +
                           score.getExperience() * weights.getExperience() +
                           score.getEducation() * weights.getEducation() +
                           score.getSoftSkills() * weights.getSoftSkills() +
                           score.getCultureFit() * weights.getCultureFit()) / 100.0;

        // A retried session re-scores candidates that already have a row; candidate_scores is one-to-one,
        // so update it in place instead of inserting a second score for the same candidate
        CandidateScore candidateScore = candidate.getScore() != null ? candidate.getScore() : new CandidateScore();
        candidateScore.setCandidate(candidate);
        candidateScore.setTechnicalSkills((double) score.getTechnicalSkills());
        candidateScore.setExperience((double) score.getExperience());
        candidateScore.setEducation((double) score.getEducation());
        candidateScore.setSoftSkills((double) score.getSoftSkills());
        candidateScore.setCultureFit((double) score.getCultureFit());
        candidateScore.setCompositeScore(composite);
        candidateScore.setRank(null);
        candidateScore.setSkills(score.getSkills());
        candidateScore.setJustification(score.getJustification());
        candidateScore.setStrengths(score.getStrengths());
        candidateScore.setGaps(score.getGaps());

        candidateScore = candidateScoreRepository.save(candidateScore);
        candidate.setScore(candidateScore);
        candidate.setStatus(CandidateStatus.SCORED);
        candidate.setFailureReason(null);
        candidateRepository.save(candidate);

        BatchJob currentJob = batchJobRepository.findById(job.getId()).orElse(job);
        currentJob.setProcessed(currentJob.getProcessed() + 1);
        batchJobRepository.save(currentJob);
        log.info("Candidate scored, composite={}", String.format("%.2f", composite));
        MDC.remove("candidateId");
    }

    @Transactional
    public void handleCandidateFailure(Candidate detached, BatchJob job, String reason) {
        Candidate candidate = candidateRepository.findById(detached.getId()).orElse(detached);
        candidate.setStatus(CandidateStatus.FAILED);
        candidate.setFailureReason(reason == null || reason.length() <= MAX_REASON_LENGTH
                ? reason : reason.substring(0, MAX_REASON_LENGTH));
        candidateRepository.save(candidate);

        BatchJob currentJob = batchJobRepository.findById(job.getId()).orElse(job);
        currentJob.setFailed(currentJob.getFailed() + 1);
        batchJobRepository.save(currentJob);
    }
}
