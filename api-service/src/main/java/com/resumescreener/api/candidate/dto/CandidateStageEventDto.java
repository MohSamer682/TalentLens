package com.resumescreener.api.candidate.dto;

import com.resumescreener.common.session.CandidateStageEvent;

import java.time.Instant;

public record CandidateStageEventDto(
    String id,
    String candidateId,
    String stage,
    String actorId,
    String actorName,
    String summary,
    Instant createdAt
) {
    public static CandidateStageEventDto from(CandidateStageEvent event) {
        return new CandidateStageEventDto(
            event.getId(),
            event.getCandidateId(),
            event.getStage(),
            event.getActorId(),
            event.getActorName(),
            event.getSummary(),
            event.getCreatedAt()
        );
    }
}
