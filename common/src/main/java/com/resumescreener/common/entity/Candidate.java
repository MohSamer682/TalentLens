package com.resumescreener.common.entity;

import com.resumescreener.common.enums.CandidateStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "candidates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ScreeningSession session;

    private String name;
    private String email;
    private String resumeFileKey;
    @Column(columnDefinition = "TEXT")
    private String rawText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CandidateStatus status;

    @OneToOne(mappedBy = "candidate", cascade = CascadeType.ALL)
    private CandidateScore score;
}