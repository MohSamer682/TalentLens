package com.resumescreener.api.admin;

import com.resumescreener.api.admin.dto.RenameRoleRequest;
import com.resumescreener.api.admin.dto.RoleDto;
import com.resumescreener.api.admin.dto.RoleHistoryItem;
import com.resumescreener.api.admin.dto.RolesResponse;
import com.resumescreener.api.audit.AuditService;
import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.profile.AdminUserService;
import com.resumescreener.common.audit.AuditEventRepository;
import com.resumescreener.common.user.Permission;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.RoleEntity;
import com.resumescreener.common.user.RolePermission;
import com.resumescreener.common.user.RolePermissionRepository;
import com.resumescreener.common.user.RoleRepository;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserRepository;
import com.resumescreener.common.user.UserRow;
import com.resumescreener.common.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    static final String STALE = "Someone changed this role while you were editing. Reload to see their changes.";

    private final RolePermissionRepository grants;
    private final RoleRepository roles;
    private final UserRepository users;
    private final AdminUserService adminUsers;
    private final AuditEventRepository auditEvents;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public RolesResponse catalog() {
        List<RolesResponse.PermissionDto> permissions = Arrays.stream(Permission.values())
                .map(p -> new RolesResponse.PermissionDto(p.name(), p.description(), p.adminOnly()))
                .toList();
        Map<Role, Long> members = activeMembers();
        Map<Role, RoleEntity> stored = roles.findAll().stream().collect(Collectors.toMap(RoleEntity::getCode, r -> r));
        List<RoleDto> list = Arrays.stream(Role.values())
                .map(r -> toDto(stored.get(r), current(r), members.getOrDefault(r, 0L)))
                .toList();
        return new RolesResponse(permissions, list);
    }

    @Transactional(readOnly = true)
    public RoleDto detail(Role role) {
        return toDto(entity(role), current(role), users.countByRoleAndStatus(role, UserStatus.ACTIVE));
    }

    @Transactional
    public RoleDto rename(Role role, RenameRoleRequest request, String actorEmail) {
        RoleEntity entity = lockedAt(role, request.version());
        String name = request.name().trim();
        String description = request.description() == null || request.description().isBlank() ? null : request.description().trim();
        roles.findByNameIgnoreCase(name).filter(other -> other.getCode() != role).ifPresent(other -> {
            throw ApiException.conflict("Another role already uses this name", "Name taken by " + other.getCode());
        });
        StringBuilder diff = new StringBuilder();
        if (!entity.getName().equals(name)) diff.append("name \"").append(entity.getName()).append("\" -> \"").append(name).append('"');
        if (!Objects.equals(entity.getDescription(), description)) diff.append(diff.isEmpty() ? "" : "; ").append("description updated");
        if (diff.isEmpty()) return detail(role);

        entity.rename(name, description, actor(actorEmail).getId(), clock.instant());
        roles.saveAndFlush(entity);
        auditService.record("ROLE_UPDATE", "role", role.name(), "success", diff.toString());
        return detail(role);
    }

    /** Replaces the role's grants in one transaction and audits what changed, e.g. "HR_CHIEF +ANALYTICS_VIEW -SESSION_VIEW_ALL". */
    @Transactional
    public RoleDto update(Role role, Set<Permission> requested, long version, String actorEmail) {
        if (role == Role.ADMIN) {
            throw ApiException.validation("The admin role always has every permission",
                    "The ADMIN row cannot be edited");
        }
        if (role == Role.CANDIDATE) {
            throw ApiException.validation("Candidates can't hold permissions", "The CANDIDATE row cannot be edited");
        }
        Set<Permission> adminOnly = requested.stream().filter(Permission::adminOnly).collect(Collectors.toSet());
        if (!adminOnly.isEmpty()) {
            throw ApiException.validation("Only admins can manage roles, users or the audit log",
                    "Cannot grant " + adminOnly + " to " + role);
        }
        lockedAt(role, version);
        String actorId = actor(actorEmail).getId();
        Set<Permission> before = current(role);
        Set<Permission> after = requested.isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(requested);

        grants.deleteByRole(role);
        grants.saveAll(after.stream().map(p -> new RolePermission(role, p)).toList());
        // The delete above clears the persistence context, so the row is read again before it is marked as changed
        RoleEntity entity = entity(role);
        entity.touch(actorId, clock.instant());
        roles.saveAndFlush(entity);

        StringBuilder diff = new StringBuilder(role.name());
        after.stream().filter(p -> !before.contains(p)).forEach(p -> diff.append(" +").append(p));
        before.stream().filter(p -> !after.contains(p)).forEach(p -> diff.append(" -").append(p));
        auditService.record("ROLE_PERMISSIONS_CHANGE", "role", role.name(), "success", diff.toString());
        return detail(role);
    }

    @Transactional(readOnly = true)
    public Page<UserRow> members(Role role, String query, Pageable pageable) {
        return adminUsers.list(null, role, query, pageable);
    }

    /** Moves people into the role and records the move on this role and on each role they left. */
    @Transactional
    public RoleDto moveMembers(Role role, Collection<String> userIds, String actorEmail) {
        Map<User, Role> moved = adminUsers.moveToRole(role, userIds, actorEmail);
        if (!moved.isEmpty()) {
            Map<Role, List<String>> bySource = new EnumMap<>(Role.class);
            moved.forEach((user, from) -> bySource.computeIfAbsent(from, r -> new java.util.ArrayList<>()).add(user.getEmail()));
            bySource.forEach((from, emails) -> {
                String details = from + " -> " + role + ": " + String.join(", ", emails);
                auditService.record("ROLE_MEMBERS_MOVED", "role", role.name(), "success", details);
                auditService.record("ROLE_MEMBERS_MOVED", "role", from.name(), "success", details);
            });
        }
        return detail(role);
    }

    @Transactional(readOnly = true)
    public Page<RoleHistoryItem> history(Role role, Pageable pageable) {
        return auditEvents.findByTargetTypeAndTargetIdOrderByOccurredAtDesc("role", role.name(), pageable).map(RoleHistoryItem::of);
    }

    private RoleEntity lockedAt(Role role, long version) {
        RoleEntity entity = roles.findForUpdateByCode(role).orElseThrow(() -> missing(role));
        if (entity.getVersion() != version) {
            throw ApiException.conflict(STALE, "Role " + role + " is at version " + entity.getVersion() + ", not " + version);
        }
        return entity;
    }

    private Map<Role, Long> activeMembers() {
        Map<Role, Long> counts = new EnumMap<>(Role.class);
        for (Object[] row : users.countByRoleGrouped(UserStatus.ACTIVE)) counts.put((Role) row[0], (Long) row[1]);
        return counts;
    }

    private Set<Permission> current(Role role) {
        return Permission.effective(role, grants.findByRole(role).stream().map(RolePermission::getPermission).toList());
    }

    private RoleEntity entity(Role role) {
        return roles.findById(role).orElseThrow(() -> missing(role));
    }

    private User actor(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow(() -> ApiException.notFound("User not found", "No user for " + email));
    }

    private static ApiException missing(Role role) {
        return ApiException.notFound("Role not found", "No roles row for " + role);
    }

    private static RoleDto toDto(RoleEntity entity, Set<Permission> permissions, long members) {
        Role role = entity.getCode();
        boolean editable = role != Role.ADMIN && role != Role.CANDIDATE;
        return new RoleDto(role, entity.getName(), entity.getDescription(), role.kind(), permissions, editable, members,
                role.uses(), entity.getVersion(), entity.getUpdatedAt());
    }
}
