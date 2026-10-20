package com.resumescreener.api.reschedule;

import com.resumescreener.api.common.ApiResponse;
import com.resumescreener.api.reschedule.dto.AcceptRescheduleBody;
import com.resumescreener.api.reschedule.dto.DeclineRescheduleBody;
import com.resumescreener.api.reschedule.dto.RescheduleRequestBody;
import com.resumescreener.api.reschedule.dto.RescheduleView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Candidates ask under {@code /api/portal}; HR answers on the candidate's page. */
@RestController
@RequiredArgsConstructor
public class RescheduleController {

    private final RescheduleService service;

    @PostMapping("/api/portal/interviews/{interviewId}/reschedule-requests")
    public ResponseEntity<ApiResponse<RescheduleView>> request(@PathVariable String interviewId, @Valid @RequestBody RescheduleRequestBody body,
                                                               Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Request sent", service.request(auth.getName(), interviewId, body)));
    }

    @PostMapping("/api/portal/reschedule-requests/{requestId}/withdraw")
    public ResponseEntity<ApiResponse<RescheduleView>> withdraw(@PathVariable String requestId, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Request withdrawn", service.withdraw(auth.getName(), requestId)));
    }

    @GetMapping("/api/sessions/{sessionId}/candidates/{candidateId}/reschedule-requests")
    public ResponseEntity<ApiResponse<List<RescheduleView>>> list(@PathVariable String sessionId, @PathVariable String candidateId,
                                                                  Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Reschedule requests", service.listForCandidate(sessionId, candidateId, auth.getName())));
    }

    @PostMapping("/api/sessions/{sessionId}/candidates/{candidateId}/reschedule-requests/{requestId}/accept")
    @PreAuthorize("hasAuthority('PIPELINE_MANAGE')")
    public ResponseEntity<ApiResponse<RescheduleView>> accept(@PathVariable String sessionId, @PathVariable String candidateId,
                                                              @PathVariable String requestId, @Valid @RequestBody AcceptRescheduleBody body,
                                                              Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Interview moved", service.accept(sessionId, candidateId, requestId, body, auth.getName())));
    }

    @PostMapping("/api/sessions/{sessionId}/candidates/{candidateId}/reschedule-requests/{requestId}/decline")
    @PreAuthorize("hasAuthority('PIPELINE_MANAGE')")
    public ResponseEntity<ApiResponse<RescheduleView>> decline(@PathVariable String sessionId, @PathVariable String candidateId,
                                                               @PathVariable String requestId, @Valid @RequestBody DeclineRescheduleBody body,
                                                               Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Request declined", service.decline(sessionId, candidateId, requestId, body, auth.getName())));
    }
}
