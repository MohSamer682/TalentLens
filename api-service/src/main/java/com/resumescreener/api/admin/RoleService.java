package com.resumescreener.api.admin;

import com.resumescreener.api.admin.dto.RoleDto;
import com.resumescreener.api.admin.dto.RolesResponse;
import com.resumescreener.api.audit.AuditService;
import com.resumescreener.api.common.ApiException;
import com.resumescreener.common.user.Permission;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.RolePermission;
import com.resumescreener.common.user.RolePermissionRepository;
import com.resumescreener.common.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RolePermissionRepository grants;
    private final UserRepository users;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public RolesResponse catalog() {
        List<RolesResponse.PermissionDto> permissions = Arrays.stream(Permission.values())
                .map(p -> new RolesResponse.PermissionDto(p.name(), p.description(), p.adminOnly()))
                .toList();
        List<RoleDto> roles = Arrays.stream(Role.values()).map(this::toDto).toList();
        return new RolesResponse(permissions, roles);
    }

    /** Replaces the role's grants in one transaction and audits what changed, e.g. "HR_CHIEF +ANALYTICS_VIEW -SESSION_VIEW_ALL". */
    @Transactional
    public RoleDto update(Role role, Set<Permission> requested) {
        if (role == Role.ADMIN) {
            throw ApiException.validation("The admin role always has every permission",
                    "The ADMIN row cannot be edited");
        }
        Set<Permission> adminOnly = requested.stream().filter(Permission::adminOnly).collect(Collectors.toSet());
        if (!adminOnly.isEmpty()) {
            throw ApiException.validation("Only admins can manage roles, users or the audit log",
                    "Cannot grant " + adminOnly + " to " + role);
        }
        Set<Permission> before = current(role);
        Set<Permission> after = requested.isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(requested);

        grants.deleteByRole(role);
        grants.saveAll(after.stream().map(p -> new RolePermission(role, p)).toList());

        StringBuilder diff = new StringBuilder(role.name());
        after.stream().filter(p -> !before.contains(p)).forEach(p -> diff.append(" +").append(p));
        before.stream().filter(p -> !after.contains(p)).forEach(p -> diff.append(" -").append(p));
        auditService.record("ROLE_PERMISSIONS_CHANGE", "role", role.name(), "success", diff.toString());
        return toDto(role, after);
    }

    private Set<Permission> current(Role role) {
        return Permission.effective(role, grants.findByRole(role).stream().map(RolePermission::getPermission).toList());
    }

    private RoleDto toDto(Role role) {
        return toDto(role, current(role));
    }

    private RoleDto toDto(Role role, Set<Permission> permissions) {
        return new RoleDto(role, permissions, role != Role.ADMIN, users.countByRole(role));
    }
}
