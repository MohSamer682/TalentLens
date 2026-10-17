package com.resumescreener.api.candidate.dto;

import com.resumescreener.common.session.InterviewPlace;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record RescheduleInterviewRequest(
    @NotNull(message = "Scheduled start time is required")
    Instant scheduledStart,

    @Min(value = 15, message = "Duration must be at least 15 minutes")
    @Max(value = 240, message = "Duration cannot exceed 240 minutes")
    Integer durationMinutes,

    InterviewPlace placeType,

    @Size(max = 500, message = "Location cannot exceed 500 characters")
    String location
) {}
