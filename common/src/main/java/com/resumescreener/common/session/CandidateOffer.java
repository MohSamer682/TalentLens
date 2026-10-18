package com.resumescreener.common.session;

import com.resumescreener.common.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

@Entity
@Table(name = "candidate_offers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateOffer {

    @Id
    @UuidGenerator
    private String id;

    @Column(name = "candidate_id", nullable = false)
    private String candidateId;

    @Column(name = "file_key", nullable = false, length = 500)
    private String fileKey;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    @Builder.Default
    private Instant uploadedAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    /** Set by the recruiter on upload: the department director signs before the HR chief */
    @Column(name = "requires_director_approval", nullable = false)
    private boolean requiresDirectorApproval;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "director_approved_by")
    private User directorApprovedBy;

    @Column(name = "director_approved_at")
    private Instant directorApprovedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rejected_by")
    private User rejectedBy;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "rejection_note", length = 1000)
    private String rejectionNote;

    @Column(name = "sent_at")
    private Instant sentAt;

    /** ACCEPTED or DECLINED once the candidate answers */
    @Column(name = "candidate_response", length = 16)
    private String candidateResponse;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "joining_date")
    private java.time.LocalDate joiningDate;

    @Column(name = "joining_notes", length = 2000)
    private String joiningNotes;
}
