package com.resumescreener.api.admin.dto;

import java.util.List;

public record RolesResponse(List<PermissionDto> permissions, List<RoleDto> roles) {
    public record PermissionDto(String name, String description, boolean adminOnly) {}
}
