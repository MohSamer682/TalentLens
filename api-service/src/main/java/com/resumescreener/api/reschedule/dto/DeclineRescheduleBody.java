package com.resumescreener.api.reschedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeclineRescheduleBody(@NotBlank(message = "Tell the candidate why") @Size(max = 1000) String note, @NotNull Long version) {
}
