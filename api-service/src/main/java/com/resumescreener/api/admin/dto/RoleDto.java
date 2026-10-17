package com.resumescreener.api.admin.dto;

import com.resumescreener.common.user.Permission;
import com.resumescreener.common.user.Role;

import java.util.Set;

public record RoleDto(Role role, Set<Permission> permissions, boolean editable, long userCount) {}
