package com.resumescreener.api.auth;

import com.resumescreener.api.auth.dto.*;
import com.resumescreener.api.common.ApiResponse;
import com.resumescreener.api.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuditService auditService;
    private final LoginThrottle throttle;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        AuthResponse response = authService.register(request);
        auditService.record("REGISTER", "user", response.getUser().getId(), "success");
        return ResponseEntity.ok(ApiResponse.success("Registration successful", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthResponse.UserDto>> me(org.springframework.security.core.Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Current user", authService.me(auth.getName())));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        // Throttled like the staff door; otherwise a staff password could be guessed here, with WRONG_PORTAL marking a hit
        throttle.check(request.getEmail());
        try {
            AuthResponse response = authService.login(request, Portal.CANDIDATE);
            throttle.succeeded(request.getEmail());
            auditService.record("LOGIN", "user", response.getUser().getId(), "success");
            return ResponseEntity.ok(ApiResponse.success("Login successful", response));
        } catch (BadCredentialsException | com.resumescreener.api.common.ApiException e) {
            throttle.failed(request.getEmail());
            auditService.record("LOGIN", "user", null, "failure", "Invalid credentials");
            throw e;
        }
    }
}
