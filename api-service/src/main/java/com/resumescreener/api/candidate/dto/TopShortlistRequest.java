package com.resumescreener.api.candidate.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record TopShortlistRequest(@Min(1) @Max(200) int count) {}
