package com.resumescreener.api.auth;

import com.resumescreener.api.audit.AuditService;
import com.resumescreener.api.auth.dto.AuthResponse;
import com.resumescreener.api.auth.dto.LoginRequest;
import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The staff door. Candidates sign in at {@code /api/auth/login}. */
@RestController
@RequestMapping("/api/admins/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthService authService;
    private final AuditService auditService;
    private final LoginThrottle throttle;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        throttle.check(request.getEmail());
        try {
            AuthResponse response = authService.login(request, Portal.STAFF);
            throttle.succeeded(request.getEmail());
            auditService.record("LOGIN", "user", response.getUser().getId(), "success");
            return ResponseEntity.ok(ApiResponse.success("Login successful", response));
        } catch (BadCredentialsException | ApiException e) {
            throttle.failed(request.getEmail());
            auditService.record("STAFF_LOGIN_FAILED", "user", null, "failure", request.getEmail());
            throw e;
        }
    }
}
