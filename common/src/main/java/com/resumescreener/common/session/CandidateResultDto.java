package com.resumescreener.common.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateResultDto {
    private String id;
    private int rank;
    private String name;
    private String email;
    private double technicalSkills;
    private double experience;
    private double education;
    private double softSkills;
    private double cultureFit;
    private double compositeScore;
    private ReviewStatus reviewStatus;
}
