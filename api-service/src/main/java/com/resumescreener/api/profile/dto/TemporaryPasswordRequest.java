package com.resumescreener.api.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TemporaryPasswordRequest(@NotBlank @Size(min = 10, max = 128) String temporaryPassword) {
}
