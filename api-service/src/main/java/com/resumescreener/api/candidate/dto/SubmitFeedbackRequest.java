package com.resumescreener.api.candidate.dto;

import com.resumescreener.common.session.InterviewRecommendation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitFeedbackRequest(
    @NotNull(message = "Recommendation is required")
    InterviewRecommendation recommendation,

    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating cannot exceed 5")
    int rating,

    @NotBlank(message = "Written assessment is required")
    @Size(min = 20, max = 5000, message = "Assessment must be between 20 and 5,000 characters")
    String assessment
) {}
