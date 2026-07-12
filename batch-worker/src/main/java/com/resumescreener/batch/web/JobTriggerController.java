package com.resumescreener.batch.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class JobTriggerController {

    private final JobService jobService;

    @PostMapping("/jobs/start")
    public ResponseEntity<Void> startJob(@RequestBody Map<String, String> request) {
        String sessionId = request.get("sessionId");
        if (sessionId == null) {
            return ResponseEntity.badRequest().build();
        }
        
        jobService.processCandidates(sessionId);
        return ResponseEntity.accepted().build();
    }
}
