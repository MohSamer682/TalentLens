package com.resumescreener.common.session;

import jakarta.persistence.*;
import lombok.*;

/** Extracted resume text, kept out of {@link Candidate} so list queries never read it. */
@Entity
@Table(name = "candidate_texts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CandidateText {

    @Id
    @Column(name = "candidate_id")
    private String candidateId;

    @Column(name = "raw_text", columnDefinition = "TEXT", nullable = false)
    private String rawText;
}
