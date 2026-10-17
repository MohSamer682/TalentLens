package com.resumescreener.common.session;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "candidate_scores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateScore {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    private Double technicalSkills;
    private Double experience;
    private Double education;
    private Double softSkills;
    private Double cultureFit;
    private Double compositeScore;
    private Integer rank;
    @Column(columnDefinition = "TEXT")
    private String justification;
    @Column(columnDefinition = "TEXT")
    private String strengths;
    @Column(columnDefinition = "TEXT")
    private String gaps;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private java.util.List<String> skills;
}