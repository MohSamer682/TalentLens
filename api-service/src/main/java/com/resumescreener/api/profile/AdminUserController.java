package com.resumescreener.api.profile;

import com.resumescreener.api.common.ApiResponse;
import com.resumescreener.api.profile.dto.*;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.UserRow;
import com.resumescreener.common.user.UserStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasAuthority('USER_MANAGE')")
@RequiredArgsConstructor
public class AdminUserController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminUserService service;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<UserRow>>> list(@RequestParam(required = false) UserStatus status,
                                                           @RequestParam(required = false) Role role,
                                                           @RequestParam(required = false) String q,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by("fullName"));
        return ResponseEntity.ok(ApiResponse.success("Users loaded", service.list(status, role, q, pageable)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProfileView>> create(@Valid @RequestBody CreateUserRequest request, Authentication auth) {
        ProfileView created = service.create(request, auth.getName());
        return ResponseEntity.created(URI.create("/api/admin/users/" + created.id()))
                .body(ApiResponse.success("User created", created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProfileView>> get(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("User loaded", service.get(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProfileView>> update(@PathVariable String id, @Valid @RequestBody UpdateUserRequest request,
                                                           Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("User saved", service.update(id, request, auth.getName())));
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<ApiResponse<ProfileView>> changeRole(@PathVariable String id, @Valid @RequestBody RoleChangeRequest request,
                                                               Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("User role updated", service.changeRole(id, request.role(), auth.getName())));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ProfileView>> setStatus(@PathVariable String id, @Valid @RequestBody StatusRequest request,
                                                              Authentication auth) {
        String message = request.status() == UserStatus.HIDDEN ? "Account hidden" : "Account restored";
        return ResponseEntity.ok(ApiResponse.success(message, service.setStatus(id, request.status(), auth.getName())));
    }

    @PostMapping("/{id}/password")
    public ResponseEntity<ApiResponse<ProfileView>> resetPassword(@PathVariable String id, @Valid @RequestBody TemporaryPasswordRequest request,
                                                                  Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Temporary password set",
                service.resetPassword(id, request.temporaryPassword(), auth.getName())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id, Authentication auth) {
        service.delete(id, auth.getName());
        return ResponseEntity.ok(ApiResponse.success("User deleted", null));
    }
}
