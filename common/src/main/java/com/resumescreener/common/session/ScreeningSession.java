package com.resumescreener.common.session;

import com.resumescreener.common.user.User;

import com.resumescreener.common.session.SessionStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "screening_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScreeningSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 160)
    private String jobTitle;

    @Column(length = 40)
    private String positionCode;

    @Column(columnDefinition = "text")
    private String jobDescription;

    /** In the order the recruiter entered them. Replace the whole list to reorder, so rows are rewritten, not swapped. */
    @ElementCollection
    @CollectionTable(name = "session_required_skills", joinColumns = @JoinColumn(name = "session_id"))
    @OrderColumn(name = "position")
    @Column(name = "skill", nullable = false, length = 80)
    @Builder.Default
    private java.util.List<String> requiredSkills = new java.util.ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    private String jdFileKey;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant completedAt;

    private String reportFileKey;

    @Column(columnDefinition = "text")
    private String lastErrorMessage;

    @OneToOne(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private ScoringWeights weights;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<Candidate> candidates;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<BatchJob> batchJobs;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (jobTitle == null) {
            jobTitle = title;
        }
    }
    }