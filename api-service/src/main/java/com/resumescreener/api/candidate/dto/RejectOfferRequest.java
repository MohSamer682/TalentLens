package com.resumescreener.api.candidate.dto;

import jakarta.validation.constraints.Size;

/** The note is optional: an approver may reject without saying why. */
public record RejectOfferRequest(
    @Size(max = 1000, message = "Note cannot exceed 1,000 characters")
    String note
) {}
