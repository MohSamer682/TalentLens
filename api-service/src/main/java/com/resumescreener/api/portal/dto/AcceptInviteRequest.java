package com.resumescreener.api.portal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInviteRequest(@NotBlank @Size(min = 10, max = 128) String password) {
}
