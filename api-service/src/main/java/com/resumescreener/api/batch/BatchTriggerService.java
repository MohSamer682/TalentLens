package com.resumescreener.api.batch;

import com.resumescreener.common.entity.*;
import com.resumescreener.common.enums.*;
import com.resumescreener.common.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
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
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (session.getStatus() == SessionStatus.RUNNING) {
            throw new RuntimeException("Session is already running");
        }

        if (session.getJdFileKey() == null) {
            throw new RuntimeException("JD file not uploaded");
        }

        long candidateCount = candidateRepository.countBySessionId(sessionId);
        if (candidateCount == 0) {
            throw new RuntimeException("No candidates uploaded");
        }

        session.setStatus(SessionStatus.RUNNING);
        sessionRepository.save(session);

        try {
            Map<String, String> body = new HashMap<>();
            body.put("sessionId", sessionId);

            restClient.post()
                    .uri(batchWorkerUrl + "/internal/jobs/start")
                    .header("X-Internal-Secret", sharedSecret)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
                    
        } catch (Exception e) {
            session.setStatus(SessionStatus.FAILED);
            sessionRepository.save(session);
            throw new RuntimeException("Failed to trigger batch: " + e.getMessage());
        }
    }
}