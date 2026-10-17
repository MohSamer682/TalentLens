package com.resumescreener.api.candidate.dto;

import com.resumescreener.common.session.ReviewStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkReviewRequest(
        @NotEmpty @Size(max = 200, message = "at most 200 candidates per request") List<String> candidateIds,
        @NotNull ReviewStatus status) {}
