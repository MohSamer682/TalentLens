package com.resumescreener.api.portal;

import com.resumescreener.api.auth.dto.AuthResponse;
import com.resumescreener.api.common.ApiResponse;
import com.resumescreener.api.portal.dto.AcceptInviteRequest;
import com.resumescreener.api.portal.dto.InvitePreview;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** The candidate side of an access link. Preview and accept are public; claim needs a signed-in candidate. */
@RestController
@RequestMapping("/api/portal/invites/{token}")
@RequiredArgsConstructor
public class PortalInviteController {

    private final PortalAccessService service;

    @GetMapping
    public ResponseEntity<ApiResponse<InvitePreview>> preview(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success("Access link is valid", service.preview(token)));
    }

    @PostMapping("/accept")
    public ResponseEntity<ApiResponse<AuthResponse>> accept(@PathVariable String token, @Valid @RequestBody AcceptInviteRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Password set", service.accept(token, request.password())));
    }

    @PostMapping("/claim")
    public ResponseEntity<ApiResponse<Void>> claim(@PathVariable String token, Authentication auth) {
        service.claim(token, auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Application linked to your account", null));
    }
}
