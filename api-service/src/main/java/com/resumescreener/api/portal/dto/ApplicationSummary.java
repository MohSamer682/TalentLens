package com.resumescreener.api.portal.dto;

import java.time.Instant;

public record ApplicationSummary(String id, String jobTitle, String positionCode, PortalStage stage, Instant nextInterviewAt) {
}
