package com.resumescreener.api.candidate.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record JoiningRequest(
    @NotNull(message = "Joining date is required")
    LocalDate joiningDate,

    @Size(max = 2000, message = "Notes cannot exceed 2,000 characters")
    String notes
) {}
