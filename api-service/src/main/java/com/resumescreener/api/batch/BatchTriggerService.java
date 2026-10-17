package com.resumescreener.api.batch;

import com.resumescreener.api.common.ApiErrorCode;
import com.resumescreener.api.common.ApiException;
import com.resumescreener.common.audit.*;
import com.resumescreener.common.session.*;
import com.resumescreener.common.user.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchTriggerService {

    private final ScreeningSessionRepository sessionRepository;
    private final CandidateRepository candidateRepository;
    private final RestClient restClient;

    @Value("${batch-worker.url}")
    private String batchWorkerUrl;

    @Value("${batch-worker.shared-secret}")
    private String sharedSecret;

    @Transactional
    public void triggerBatch(String sessionId) {
        ScreeningSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> ApiException.notFound("Session not found",
                        "No session exists for id " + sessionId));

        if (session.getStatus() == SessionStatus.RUNNING) {
            throw ApiException.validation("Session is already running",
                    "Session " + sessionId + " is in status RUNNING");
        }

        if (session.getJdFileKey() == null) {
            throw ApiException.validation("JD file not uploaded",
                    "Session " + sessionId + " has no jdFileKey");
        }

        long candidateCount = candidateRepository.countBySessionId(sessionId);
        if (candidateCount == 0) {
            throw ApiException.validation("No candidates uploaded",
                    "Session " + sessionId + " has zero candidates");
        }

        if (sessionRepository.markRunning(sessionId) == 0) {
            throw ApiException.validation("Session is already running",
                    "Session " + sessionId + " is in status RUNNING");
        }

        log.info("Triggering batch for session {} with {} candidates", sessionId, candidateCount);

        try {
            Map<String, String> body = new HashMap<>();
            body.put("sessionId", sessionId);

            restClient.post()
                    .uri(batchWorkerUrl + "/internal/jobs/start")
                    .header("X-Internal-Secret", sharedSecret)
                    .header("X-Request-Id", MDC.get("requestId"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
                    
        } catch (Exception e) {
            log.error("Failed to trigger batch worker for session {}", sessionId, e);
            session.setStatus(SessionStatus.FAILED);
            sessionRepository.save(session);
            throw new ApiException(HttpStatus.BAD_GATEWAY, ApiErrorCode.BATCH_WORKER_UNAVAILABLE,
                    "Failed to trigger batch worker",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }
}
