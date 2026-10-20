package com.resumescreener.api.portal.dto;

import com.resumescreener.api.jobs.dto.JobDetailsDto;

import java.util.List;

public record ApplicationView(String id, JobDetailsDto job, PortalStage stage, List<StageStep> steps, List<PortalInterview> interviews,
                              Contact recruiter) {

    public record Contact(String name, String headline) {
    }
}
