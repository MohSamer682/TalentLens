package com.resumescreener.api.portal;

import com.resumescreener.api.auth.dto.AuthResponse;
import com.resumescreener.api.common.ApiResponse;
import com.resumescreener.api.portal.dto.AcceptInviteRequest;
import com.resumescreener.api.portal.dto.InvitePreview;
import com.resumescreener.api.portal.dto.InviteTokenRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * The candidate side of an access link. The token travels in the body, never the path, so no proxy or
 * access log records it. Preview and accept are public; claim needs a signed-in candidate.
 */
@RestController
@RequestMapping("/api/portal/invites")
@RequiredArgsConstructor
public class PortalInviteController {

    private final PortalAccessService service;

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<InvitePreview>> preview(@Valid @RequestBody InviteTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Access link is valid", service.preview(request.token())));
    }

    @PostMapping("/accept")
    public ResponseEntity<ApiResponse<AuthResponse>> accept(@Valid @RequestBody AcceptInviteRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Password set", service.accept(request.token(), request.password())));
    }

    @PostMapping("/claim")
    public ResponseEntity<ApiResponse<Void>> claim(@Valid @RequestBody InviteTokenRequest request, Authentication auth) {
        service.claim(request.token(), auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Application linked to your account", null));
    }
}
