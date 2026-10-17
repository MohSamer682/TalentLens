package com.resumescreener.common.session;

import com.resumescreener.common.session.CandidateStatus;
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

    /** SHA-256 of the uploaded file; one session never holds the same file twice */
    private String contentHash;
    private String originalFileName;
    @Column(columnDefinition = "TEXT")
    private String failureReason;

    @Column(nullable = false)
    private java.time.Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReviewStatus reviewStatus = ReviewStatus.NONE;
    private java.time.Instant reviewedAt;
    /** Id of the user who made the current review decision */
    private String reviewedBy;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = java.time.Instant.now();
        }
    }
}