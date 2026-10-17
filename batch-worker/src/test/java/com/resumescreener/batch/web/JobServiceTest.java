package com.resumescreener.batch.web;

import com.resumescreener.batch.excel.ExcelReportGenerator;
import com.resumescreener.batch.storage.MinioStorageService;
import com.resumescreener.common.session.BatchJob;
import com.resumescreener.common.session.BatchJobRepository;
import com.resumescreener.common.session.CandidateRepository;
import com.resumescreener.common.session.CandidateScoreRepository;
import com.resumescreener.common.session.CandidateStatus;
import com.resumescreener.common.session.ScoringScope;
import com.resumescreener.common.session.ScoringWeightsRepository;
import com.resumescreener.common.session.ScreeningSession;
import com.resumescreener.common.session.ScreeningSessionRepository;
import com.resumescreener.common.session.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobServiceTest {

    private final ScreeningSessionRepository sessions = mock(ScreeningSessionRepository.class);
    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final BatchJobRepository jobs = mock(BatchJobRepository.class);
    private final JobService service = new JobService(sessions, candidates, mock(CandidateScoreRepository.class),
            mock(ScoringWeightsRepository.class), jobs, mock(MinioStorageService.class),
            mock(ExcelReportGenerator.class), mock(CandidateProcessingService.class), mock(TransactionTemplate.class));
    private final ScreeningSession session = ScreeningSession.builder()
            .id("s1").status(SessionStatus.RUNNING).lastErrorMessage("LLM timed out").build();
    private final BatchJob job = BatchJob.builder().id("j1").processed(0).failed(2).build();

    @BeforeEach
    void setUp() {
        when(sessions.findById("s1")).thenReturn(Optional.of(session));
        when(jobs.save(any(BatchJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jobs.findById("j1")).thenReturn(Optional.of(job));
    }

    @Test
    void unscoredScopeSelectsPendingAndFailedOnly() {
        service.processCandidates("s1", ScoringScope.UNSCORED);

        verify(candidates).findBySessionIdAndStatusIn("s1", EnumSet.of(CandidateStatus.PENDING, CandidateStatus.FAILED));
        verify(candidates, never()).findBySessionId("s1");
    }

    @Test
    void allScopeSelectsEveryCandidate() {
        when(candidates.findBySessionId("s1")).thenReturn(List.of());

        service.processCandidates("s1", ScoringScope.ALL);

        verify(candidates).findBySessionId("s1");
        verify(candidates, never()).findBySessionIdAndStatusIn(any(), anyCollection());
    }

    @Test
    void finalizeKeepsDoneWhenEarlierResumesAreScored() {
        when(candidates.existsBySessionIdAndStatus("s1", CandidateStatus.SCORED)).thenReturn(true);

        service.finalizeSession("s1", job);

        assertEquals(SessionStatus.DONE, session.getStatus());
    }

    @Test
    void finalizeFailsWhenNothingInSessionIsScored() {
        service.finalizeSession("s1", job);

        assertEquals(SessionStatus.FAILED, session.getStatus());
    }
}
