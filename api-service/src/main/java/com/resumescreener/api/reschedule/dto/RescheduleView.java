package com.resumescreener.api.reschedule.dto;

import com.resumescreener.common.reschedule.InterviewRescheduleRequest;
import com.resumescreener.common.reschedule.RescheduleStatus;

import java.time.Instant;
import java.util.List;

public record RescheduleView(String id, String interviewId, RescheduleStatus status, String reason, List<Instant> proposedStarts,
                             String resolutionNote, Instant createdAt, Instant resolvedAt, long version, String requestedByName) {

    public static RescheduleView of(InterviewRescheduleRequest request, String requestedByName) {
        return new RescheduleView(request.getId(), request.getInterviewId(), request.getStatus(), request.getReason(),
                List.copyOf(request.getProposedStarts()), request.getResolutionNote(), request.getCreatedAt(), request.getResolvedAt(),
                request.getVersion(), requestedByName);
    }
}
