package com.resumescreener.api.candidate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateNoteRequest(
    @NotBlank(message = "Note cannot be empty")
    @Size(min = 1, max = 4000, message = "Note must be between 1 and 4,000 characters")
    String content
) {}
