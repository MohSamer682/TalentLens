package com.resumescreener.api.jobs.dto;

import java.util.List;

/** Job fields of a create or update; a null field means "leave it as it is". */
public record JobDetailsRequest(String title, String positionCode, String description, List<String> requiredSkills) {
}
