package com.resumescreener.common.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "scoring_weights")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScoringWeights {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ScreeningSession session;

    private Double technicalSkills;
    private Double experience;
    private Double education;
    private Double softSkills;
    private Double cultureFit;
}