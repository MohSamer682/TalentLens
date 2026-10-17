package com.resumescreener.api.candidate.dto;

import com.resumescreener.common.session.InterviewKind;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PlanInterviewsRequest(
    @NotEmpty(message = "Choose at least one interview")
    List<InterviewKind> kinds
) {}
