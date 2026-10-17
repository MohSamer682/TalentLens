package com.resumescreener.common.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmScoreDto {

    @JsonProperty("technical_skills")
    private Integer technicalSkills;

    @JsonProperty("experience")
    private Integer experience;

    @JsonProperty("education")
    private Integer education;

    @JsonProperty("soft_skills")
    private Integer softSkills;

    @JsonProperty("culture_fit")
    private Integer cultureFit;

    @JsonProperty("skills")
    private List<String> skills;

    @JsonProperty("justification")
    private String justification;

    @JsonProperty("strengths")
    private String strengths;

    @JsonProperty("gaps")
    private String gaps;
}
