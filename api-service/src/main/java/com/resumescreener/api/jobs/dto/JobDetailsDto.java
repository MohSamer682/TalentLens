package com.resumescreener.api.jobs.dto;

import java.util.List;

/** {@code requiredSkills} is null on session lists, which do not load skills. */
public record JobDetailsDto(String title, String positionCode, String description, List<String> requiredSkills) {
}
