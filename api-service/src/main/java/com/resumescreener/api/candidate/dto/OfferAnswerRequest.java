package com.resumescreener.api.candidate.dto;

import jakarta.validation.constraints.NotNull;

public record OfferAnswerRequest(
    @NotNull(message = "Say whether the candidate accepted")
    Boolean accepted
) {}
