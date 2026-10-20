package com.resumescreener.api.reschedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record RescheduleRequestBody(
        @NotBlank(message = "Tell your recruiter why you need to move it") @Size(max = 1000) String reason,
        @NotNull @Size(min = 1, max = 3, message = "Suggest one to three times") List<@NotNull Instant> proposedStarts) {
}
