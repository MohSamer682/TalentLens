package com.resumescreener.api.profile.dto;

import com.resumescreener.common.user.Role;
import jakarta.validation.constraints.NotNull;

public record RoleChangeRequest(@NotNull(message = "Role is required") Role role) {
}
