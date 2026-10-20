package com.resumescreener.api.profile.dto;

import com.resumescreener.common.user.UserStatus;
import jakarta.validation.constraints.NotNull;

public record StatusRequest(@NotNull UserStatus status) {
}
