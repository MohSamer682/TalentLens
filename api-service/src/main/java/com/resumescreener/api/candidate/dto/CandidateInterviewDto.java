package com.resumescreener.api.candidate.dto;

import com.resumescreener.common.session.CandidateInterview;
import com.resumescreener.common.session.InterviewKind;
import com.resumescreener.common.session.InterviewRecommendation;
import com.resumescreener.common.session.InterviewStatus;

import java.time.Instant;

public record CandidateInterviewDto(
    String id,
    String candidateId,
    InterviewKind kind,
    String interviewerId,
    String interviewerName,
    String interviewerRole,
    Instant scheduledStart,
    int durationMinutes,
    String location,
    String noteToInterviewer,
    InterviewStatus status,
    InterviewRecommendation recommendation,
    Integer rating,
    Instant feedbackSubmittedAt,
    String feedbackAssessment,
    String createdById,
    Instant createdAt
) {
    public static CandidateInterviewDto from(CandidateInterview interview) {
        return new CandidateInterviewDto(
            interview.getId(),
            interview.getCandidateId(),
            interview.getKind(),
            interview.getInterviewer() != null ? interview.getInterviewer().getId() : null,
            interview.getInterviewer() != null ? interview.getInterviewer().getFullName() : "Unknown",
            interview.getInterviewer() != null && interview.getInterviewer().getRole() != null ? interview.getInterviewer().getRole().name() : null,
            interview.getScheduledStart(),
            interview.getDurationMinutes(),
            interview.getLocation(),
            interview.getNoteToInterviewer(),
            interview.getStatus(),
            interview.getRecommendation(),
            interview.getRating(),
            interview.getFeedbackSubmittedAt(),
            interview.getFeedbackAssessment(),
            interview.getCreatedBy() != null ? interview.getCreatedBy().getId() : null,
            interview.getCreatedAt()
        );
    }
}
