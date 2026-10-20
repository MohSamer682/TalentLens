package com.resumescreener.batch.web;

import com.resumescreener.batch.llm.LlmStatusService;
import com.resumescreener.common.llm.LlmStatusDto;
import com.resumescreener.common.session.ScoringScope;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class JobTriggerController {

    private final JobService jobService;
    private final LlmStatusService llmStatusService;

    @PostMapping("/jobs/start")
    public ResponseEntity<Map<String, Object>> startJob(@RequestBody Map<String, String> request) {
        String sessionId = request.get("sessionId");
        if (sessionId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId is required");
        }
        ScoringScope scope;
        try {
            scope = ScoringScope.valueOf(request.getOrDefault("scope", ScoringScope.UNSCORED.name()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown scope");
        }

        jobService.processCandidates(sessionId, scope);
        return ResponseEntity.accepted().body(Map.of("success", true, "message", "Job accepted", "data", Map.of("sessionId", sessionId)));
    }

    @GetMapping("/llm/status")
    public ResponseEntity<LlmStatusDto> llmStatus() {
        return ResponseEntity.ok(llmStatusService.getStatus());
    }
}
