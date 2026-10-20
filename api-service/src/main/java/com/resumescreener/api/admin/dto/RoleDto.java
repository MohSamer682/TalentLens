package com.resumescreener.api.admin.dto;

import com.resumescreener.common.user.Permission;
import com.resumescreener.common.user.Role;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** {@code editable} is false where the permissions are fixed: ADMIN holds all of them and CANDIDATE none. */
public record RoleDto(Role role, String name, String description, Role.Kind kind, Set<Permission> permissions, boolean editable,
                      long memberCount, List<String> uses, long version, Instant updatedAt) {}
