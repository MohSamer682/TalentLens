package com.resumescreener.api.jobs;

import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.jobs.dto.JobDetailsDto;
import com.resumescreener.api.jobs.dto.JobDetailsRequest;
import com.resumescreener.common.session.ScreeningSession;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class JobDetailsMapper {

    private JobDetailsMapper() {
    }

    public static JobDetailsDto from(ScreeningSession session, boolean withSkills) {
        return new JobDetailsDto(session.getJobTitle(), session.getPositionCode(), session.getJobDescription(),
                withSkills ? List.copyOf(session.getRequiredSkills()) : null);
    }

    /** Copies only the job fields present in the request, so a PATCH can change one of them. */
    public static void apply(JobDetailsRequest request, ScreeningSession session) {
        if (request.title() != null) session.setJobTitle(request.title().trim());
        if (request.positionCode() != null) session.setPositionCode(request.positionCode().trim().toUpperCase(Locale.ROOT));
        if (request.description() != null) session.setJobDescription(request.description().trim());
        if (request.requiredSkills() != null) {
            // A new list, not clear(): Hibernate then rewrites the rows instead of shuffling them under the unique index
            session.setRequiredSkills(normalise(request.requiredSkills()));
        }
    }

    private static List<String> normalise(List<String> skills) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String skill : skills) {
            String trimmed = skill == null ? "" : skill.trim();
            if (trimmed.isEmpty()) continue;
            if (!seen.add(trimmed.toLowerCase(Locale.ROOT))) {
                throw ApiException.validation("That skill is already on the list", "requiredSkills: " + trimmed + " appears twice");
            }
            result.add(trimmed);
        }
        return result;
    }
}
