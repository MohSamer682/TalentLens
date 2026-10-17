package com.resumescreener.api.admin.dto;

import com.resumescreener.common.user.Permission;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record UpdatePermissionsRequest(@NotNull Set<Permission> permissions) {}
