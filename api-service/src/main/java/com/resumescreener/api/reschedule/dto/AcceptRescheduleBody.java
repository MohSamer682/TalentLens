package com.resumescreener.api.reschedule.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** {@code version} is the request as the answering tab saw it; a different one means someone answered first. */
public record AcceptRescheduleBody(@NotNull Instant scheduledStart, @NotNull Long version) {
}
