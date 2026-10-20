package com.resumescreener.api.session.dto;

import com.resumescreener.api.jobs.dto.JobDetailsRequest;
import com.resumescreener.common.session.WeightsDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateSessionRequest {
    @NotBlank(groups = OnCreate.class)
    private String title;
    private String description;
    @Valid
    private WeightsDto weights;

    @NotBlank(groups = OnCreate.class)
    @Size(max = 160)
    private String jobTitle;

    @NotBlank(groups = OnCreate.class)
    @Pattern(regexp = "^\\s*[A-Za-z0-9][A-Za-z0-9-]{1,39}\\s*$", message = "can use letters, numbers and dashes (2 to 40)")
    private String positionCode;

    @Size(max = 20000)
    private String jobDescription;

    @NotEmpty(groups = OnCreate.class)
    @Size(max = 30)
    private List<@NotBlank @Size(max = 80) String> requiredSkills;

    public JobDetailsRequest jobDetails() {
        return new JobDetailsRequest(jobTitle, positionCode, jobDescription, requiredSkills);
    }
}
