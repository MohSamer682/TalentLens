package com.resumescreener.api.admin;

import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.common.ApiResponse;
import com.resumescreener.api.audit.AuditService;
import com.resumescreener.common.audit.AuditEvent;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.audit.AuditEventRepository;
import com.resumescreener.common.user.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final AuditService auditService;
    private final AuditEventRepository auditEventRepository;

    public record UserDto(String id, String email, String fullName, Role role, java.time.Instant createdAt) {}

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<ApiResponse<Page<UserDto>>> listUsers(Pageable pageable) {
        Page<UserDto> data = userRepository.findAll(pageable).map(AdminController::toDto);
        return ResponseEntity.ok(ApiResponse.success("Users loaded", data));
    }

    @PatchMapping("/users/{id}/role")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<ApiResponse<UserDto>> updateRole(
            @PathVariable String id,
            @RequestBody RoleRequest request,
            org.springframework.security.core.Authentication auth) {

        if (request == null || request.getRole() == null) {
            throw ApiException.validation("Role is required", "Request body had no role value");
        }

        User caller = userRepository.findByEmailIgnoreCase(auth.getName())
                .orElseThrow(() -> ApiException.notFound("Caller not found",
                        "No user exists for email " + auth.getName()));

        if (caller.getId().equals(id)) {
            throw ApiException.validation("You cannot change your own role",
                    "Rejected role update for the calling user " + id);
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found",
                        "No user exists for id " + id));

        Role oldRole = user.getRole();
        user.setRole(request.getRole());
        userRepository.save(user);

        auditService.record("ROLE_CHANGE", "user", id, "success",
                oldRole + " -> " + request.getRole());

        return ResponseEntity.ok(ApiResponse.success("User role updated", toDto(user)));
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    public ResponseEntity<ApiResponse<Page<AuditEvent>>> getAuditLog(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String action,
            Pageable pageable) {
        Page<AuditEvent> data = auditEventRepository.findFiltered(userId, action, pageable);
        return ResponseEntity.ok(ApiResponse.success("Audit log loaded", data));
    }

    private static UserDto toDto(User user) {
        return new UserDto(user.getId(), user.getEmail(), user.getFullName(),
                user.getRole(), user.getCreatedAt());
    }

    @Data
    public static class RoleRequest {
        private Role role;
    }
}
