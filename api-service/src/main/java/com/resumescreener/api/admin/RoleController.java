package com.resumescreener.api.admin;

import com.resumescreener.api.admin.dto.RoleDto;
import com.resumescreener.api.admin.dto.RolesResponse;
import com.resumescreener.api.admin.dto.UpdatePermissionsRequest;
import com.resumescreener.api.common.ApiResponse;
import com.resumescreener.common.user.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<ApiResponse<RolesResponse>> list() {
        return ResponseEntity.ok(ApiResponse.success("Roles loaded", roleService.catalog()));
    }

    @PutMapping("/{role}/permissions")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<ApiResponse<RoleDto>> update(@PathVariable Role role,
                                                       @Valid @RequestBody UpdatePermissionsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Permissions saved", roleService.update(role, request.permissions())));
    }
}
