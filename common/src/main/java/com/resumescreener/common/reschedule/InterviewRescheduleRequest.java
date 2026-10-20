package com.resumescreener.common.reschedule;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ListIndexBase;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A candidate's ask to move one interview, with up to three times that suit them. */
@Entity
@Table(name = "interview_reschedule_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewRescheduleRequest {

    @Id
    @UuidGenerator
    private String id;

    @Column(nullable = false)
    private String interviewId;

    @Column(nullable = false)
    private String requestedBy;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RescheduleStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "interview_reschedule_slots", joinColumns = @JoinColumn(name = "request_id"))
    @OrderColumn(name = "slot_no")
    @ListIndexBase(1)
    @Column(name = "proposed_start", nullable = false)
    private List<Instant> proposedStarts = new ArrayList<>();

    @Column(length = 1000)
    private String resolutionNote;

    private String resolvedBy;
    private Instant resolvedAt;

    @Column(nullable = false)
    private Instant createdAt;

    /** A stale tab answering an already answered request fails here rather than overwriting it. */
    @Version
    private long version;

    public static InterviewRescheduleRequest open(String interviewId, String requesterId, String reason, List<Instant> proposedStarts, Instant now) {
        InterviewRescheduleRequest request = new InterviewRescheduleRequest();
        request.interviewId = interviewId;
        request.requestedBy = requesterId;
        request.reason = reason;
        request.proposedStarts = new ArrayList<>(proposedStarts);
        request.status = RescheduleStatus.PENDING;
        request.createdAt = now;
        return request;
    }

    public void accept(String byUserId, Instant at) {
        resolve(RescheduleStatus.ACCEPTED, byUserId, null, at);
    }

    public void decline(String byUserId, String note, Instant at) {
        resolve(RescheduleStatus.DECLINED, byUserId, note, at);
    }

    public void withdraw(Instant at) {
        resolve(RescheduleStatus.WITHDRAWN, requestedBy, null, at);
    }

    private void resolve(RescheduleStatus target, String byUserId, String note, Instant at) {
        status = status.moveTo(target);
        resolvedBy = byUserId;
        resolutionNote = note;
        resolvedAt = at;
    }
}
