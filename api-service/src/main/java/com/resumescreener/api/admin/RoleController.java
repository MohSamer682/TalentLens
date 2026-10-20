package com.resumescreener.api.admin;

import com.resumescreener.api.admin.dto.MoveMembersRequest;
import com.resumescreener.api.admin.dto.RenameRoleRequest;
import com.resumescreener.api.admin.dto.RoleDto;
import com.resumescreener.api.admin.dto.RoleHistoryItem;
import com.resumescreener.api.admin.dto.RolesResponse;
import com.resumescreener.api.admin.dto.UpdatePermissionsRequest;
import com.resumescreener.api.common.ApiResponse;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.UserRow;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
public class RoleController {

    static final int MAX_PAGE = 100;

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<ApiResponse<RolesResponse>> list() {
        return ResponseEntity.ok(ApiResponse.success("Roles loaded", roleService.catalog()));
    }

    @GetMapping("/{role}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<ApiResponse<RoleDto>> detail(@PathVariable Role role) {
        return ResponseEntity.ok(ApiResponse.success("Role loaded", roleService.detail(role)));
    }

    @PatchMapping("/{role}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<ApiResponse<RoleDto>> rename(@PathVariable Role role, @Valid @RequestBody RenameRoleRequest request, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Role saved", roleService.rename(role, request, auth.getName())));
    }

    @PutMapping("/{role}/permissions")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<ApiResponse<RoleDto>> update(@PathVariable Role role,
                                                       @Valid @RequestBody UpdatePermissionsRequest request, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Permissions saved",
                roleService.update(role, request.permissions(), request.version(), auth.getName())));
    }

    @GetMapping("/{role}/members")
    @PreAuthorize("hasAuthority('ROLE_MANAGE') and hasAuthority('USER_MANAGE')")
    public ResponseEntity<ApiResponse<Page<UserRow>>> members(@PathVariable Role role, @RequestParam(required = false) String q,
                                                             @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE), Sort.by("fullName", "id"));
        return ResponseEntity.ok(ApiResponse.success("Members loaded", roleService.members(role, q, pageable)));
    }

    @PostMapping("/{role}/members")
    @PreAuthorize("hasAuthority('ROLE_MANAGE') and hasAuthority('USER_MANAGE')")
    public ResponseEntity<ApiResponse<RoleDto>> move(@PathVariable Role role, @Valid @RequestBody MoveMembersRequest request, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("People moved", roleService.moveMembers(role, request.userIds(), auth.getName())));
    }

    @GetMapping("/{role}/history")
    @PreAuthorize("hasAuthority('ROLE_MANAGE') and hasAuthority('AUDIT_VIEW')")
    public ResponseEntity<ApiResponse<Page<RoleHistoryItem>>> history(@PathVariable Role role, @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE));
        return ResponseEntity.ok(ApiResponse.success("History loaded", roleService.history(role, pageable)));
    }
}
