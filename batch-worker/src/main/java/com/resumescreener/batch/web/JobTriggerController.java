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
    public ResponseEntity<Void> startJob(@RequestBody Map<String, String> request) {
        String sessionId = request.get("sessionId");
        if (sessionId == null) {
            return ResponseEntity.badRequest().build();
        }
        ScoringScope scope;
        try {
            scope = ScoringScope.valueOf(request.getOrDefault("scope", ScoringScope.UNSCORED.name()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        jobService.processCandidates(sessionId, scope);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/llm/status")
    public ResponseEntity<LlmStatusDto> llmStatus() {
        return ResponseEntity.ok(llmStatusService.getStatus());
    }
}
