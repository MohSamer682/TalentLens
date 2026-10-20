package com.resumescreener.batch.web;

import com.resumescreener.batch.llm.LlmStatusService;
import com.resumescreener.common.llm.LlmStatusDto;
import com.resumescreener.common.session.ScoringScope;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class JobTriggerController {

    private final JobService jobService;
    private final LlmStatusService llmStatusService;

    @PostMapping("/jobs/start")
    public ResponseEntity<Map<String, String>> startJob(@RequestBody Map<String, String> request) {
        String sessionId = request.get("sessionId");
        if (sessionId == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "rejected", "error", "sessionId is required"));
        }
        ScoringScope scope;
        try {
            scope = ScoringScope.valueOf(request.getOrDefault("scope", ScoringScope.UNSCORED.name()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "rejected", "error", "unknown scope"));
        }

        jobService.processCandidates(sessionId, scope);
        return ResponseEntity.accepted().body(Map.of("status", "accepted", "sessionId", sessionId));
    }

    @GetMapping("/llm/status")
    public ResponseEntity<LlmStatusDto> llmStatus() {
        return ResponseEntity.ok(llmStatusService.getStatus());
    }
}
