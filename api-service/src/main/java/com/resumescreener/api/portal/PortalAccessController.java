package com.resumescreener.api.portal;

import com.resumescreener.api.common.ApiResponse;
import com.resumescreener.api.portal.dto.IssuedInvite;
import com.resumescreener.api.portal.dto.PortalAccess;
import com.resumescreener.api.profile.dto.TemporaryPasswordRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/** HR side of portal access, under the candidate it belongs to. */
@RestController
@RequestMapping("/api/sessions/{sessionId}/candidates/{candidateId}/portal")
@RequiredArgsConstructor
public class PortalAccessController {

    private final PortalAccessService service;

    @GetMapping
    public ResponseEntity<ApiResponse<PortalAccess>> access(@PathVariable String sessionId, @PathVariable String candidateId,
                                                            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Portal access loaded", service.accessOf(sessionId, candidateId, auth.getName())));
    }

    @PostMapping("/invites")
    @PreAuthorize("hasAuthority('PIPELINE_MANAGE')")
    public ResponseEntity<ApiResponse<IssuedInvite>> issue(@PathVariable String sessionId, @PathVariable String candidateId,
                                                           Authentication auth) {
        IssuedInvite issued = service.issueInvite(sessionId, candidateId, auth.getName());
        return ResponseEntity.created(URI.create("/api/sessions/" + sessionId + "/candidates/" + candidateId + "/portal"))
                .body(ApiResponse.success("Access link created", issued));
    }

    @DeleteMapping("/invites")
    @PreAuthorize("hasAuthority('PIPELINE_MANAGE')")
    public ResponseEntity<Void> revoke(@PathVariable String sessionId, @PathVariable String candidateId, Authentication auth) {
        service.revokeInvite(sessionId, candidateId, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/account")
    @PreAuthorize("hasAuthority('PIPELINE_MANAGE')")
    public ResponseEntity<ApiResponse<PortalAccess>> createAccount(@PathVariable String sessionId, @PathVariable String candidateId,
                                                                   @Valid @RequestBody TemporaryPasswordRequest request, Authentication auth) {
        PortalAccess access = service.createAccount(sessionId, candidateId, request.temporaryPassword(), auth.getName());
        return ResponseEntity.created(URI.create("/api/sessions/" + sessionId + "/candidates/" + candidateId + "/portal"))
                .body(ApiResponse.success("Portal account created", access));
    }
}
