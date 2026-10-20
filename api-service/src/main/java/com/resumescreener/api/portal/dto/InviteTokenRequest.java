package com.resumescreener.api.portal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InviteTokenRequest(@NotBlank @Size(max = 128) String token) {
}
