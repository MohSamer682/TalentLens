package com.resumescreener.api.batch;

import com.resumescreener.api.common.ApiException;
import com.resumescreener.common.session.CandidateRepository;
import com.resumescreener.common.session.CandidateStatus;
import com.resumescreener.common.session.ScoringScope;
import com.resumescreener.common.session.ScreeningSession;
import com.resumescreener.common.session.ScreeningSessionRepository;
import com.resumescreener.common.session.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withAccepted;

class BatchTriggerServiceTest {

    private final ScreeningSessionRepository sessions = mock(ScreeningSessionRepository.class);
    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final RestClient.Builder restBuilder = RestClient.builder();
    private final MockRestServiceServer worker = MockRestServiceServer.bindTo(restBuilder).build();
    private final BatchTriggerService service = new BatchTriggerService(sessions, candidates, restBuilder.build());

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "batchWorkerUrl", "http://worker");
        ReflectionTestUtils.setField(service, "sharedSecret", "secret");
        when(sessions.findById("s1")).thenReturn(Optional.of(ScreeningSession.builder()
                .id("s1").jdFileKey("jd/s1/x.pdf").status(SessionStatus.DONE).build()));
        when(candidates.countBySessionId("s1")).thenReturn(3L);
        when(sessions.markRunning("s1")).thenReturn(1);
    }

    @Test
    void unscoredScopeWithNothingToScoreIsRejected() {
        ApiException e = assertThrows(ApiException.class, () -> service.triggerBatch("s1", ScoringScope.UNSCORED));

        assertEquals("Every resume in this session is already scored", e.getMessage());
        verify(sessions, never()).markRunning(anyString());
    }

    @Test
    void sendsScopeToWorker() {
        when(candidates.countBySessionIdAndStatus("s1", CandidateStatus.PENDING)).thenReturn(2L);
        worker.expect(requestTo("http://worker/internal/jobs/start"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.scope").value("UNSCORED"))
                .andRespond(withAccepted());

        service.triggerBatch("s1", ScoringScope.UNSCORED);

        worker.verify();
    }

    @Test
    void allScopeRunsEvenWhenEverythingIsScored() {
        worker.expect(requestTo("http://worker/internal/jobs/start"))
                .andExpect(jsonPath("$.scope").value("ALL"))
                .andRespond(withAccepted());

        service.triggerBatch("s1", ScoringScope.ALL);

        verify(sessions).markRunning("s1");
        worker.verify();
    }
}
