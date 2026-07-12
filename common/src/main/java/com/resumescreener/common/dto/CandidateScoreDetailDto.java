package com.resumescreener.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateScoreDetailDto {
    private String id;
    private String candidateId;
    private double technicalSkills;
    private double experience;
    private double education;
    private double softSkills;
    private double cultureFit;
    private double compositeScore;
    private int rank;
    private List<String> skills;
    private String justification;
    private String strengths;
    private String gaps;
    private String name;
    private String email;
    private WeightsDto weights;
}
