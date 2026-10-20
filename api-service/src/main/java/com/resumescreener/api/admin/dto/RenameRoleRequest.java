package com.resumescreener.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RenameRoleRequest(@NotBlank @Size(max = 80) String name, @Size(max = 300) String description, @NotNull Long version) {}
