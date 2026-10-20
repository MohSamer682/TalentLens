package com.resumescreener.api.candidate.dto;

import com.resumescreener.common.session.ReviewStatus;

import java.time.Instant;

public record PipelineCandidateDto(
    String id,
    String sessionId,
    String sessionTitle,
    String name,
    String email,
    Double score,
    ReviewStatus stage,
    long inStageDays,
    String nextStep,
    Instant stageChangedAt,
    String offerFileName,
    String offerUploaderName,
    String offerUploadedById,
    /** Who must act on the offer next: DIRECTOR, HR_CHIEF, REJECTED; null when no approval is pending */
    String offerStep,
    /** The candidate asked to move an interview and nobody has answered yet */
    boolean rescheduleRequested
) {
    public PipelineCandidateDto(
            String id,
            String sessionId,
            String sessionTitle,
            String name,
            String email,
            Double score,
            ReviewStatus stage,
            long inStageDays,
            String nextStep,
            Instant stageChangedAt
    ) {
        this(id, sessionId, sessionTitle, name, email, score, stage, inStageDays, nextStep, stageChangedAt, null, null, null, null, false);
    }
}
