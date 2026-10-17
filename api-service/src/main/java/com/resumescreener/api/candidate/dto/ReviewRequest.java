package com.resumescreener.api.candidate.dto;

import com.resumescreener.common.session.ReviewStatus;
import jakarta.validation.constraints.NotNull;

public record ReviewRequest(@NotNull ReviewStatus status) {}
