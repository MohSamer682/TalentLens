package com.resumescreener.api.candidate.dto;

import com.resumescreener.common.session.Candidate;
import com.resumescreener.common.session.CandidateStatus;

import java.time.Instant;

public record ResumeFileDto(String id, String name, String originalFileName, CandidateStatus status,
                            String failureReason, Instant createdAt) {

    public static ResumeFileDto from(Candidate c) {
        return new ResumeFileDto(c.getId(), c.getName(), c.getOriginalFileName(), c.getStatus(),
                c.getFailureReason(), c.getCreatedAt());
    }
}
