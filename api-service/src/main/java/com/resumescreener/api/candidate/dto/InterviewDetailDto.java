package com.resumescreener.api.candidate.dto;

import com.resumescreener.common.session.InterviewKind;
import com.resumescreener.common.session.InterviewPlace;
import com.resumescreener.common.session.InterviewRecommendation;
import com.resumescreener.common.session.InterviewStatus;

import java.time.Instant;

public record InterviewDetailDto(
        String id,
        String candidateId,
        String candidateName,
        String candidateEmail,
        String sessionId,
        String sessionTitle,
        String recruiterName,
        InterviewKind kind,
        String interviewerId,
        String interviewerName,
        Instant scheduledStart,
        int durationMinutes,
        InterviewPlace placeType,
        String location,
        String noteToInterviewer,
        InterviewStatus status,
        InterviewRecommendation recommendation,
        Integer rating,
        String feedbackAssessment,
        Instant feedbackSubmittedAt,
        Double compositeScore,
        Integer rank,
        String strengths,
        String gaps,
        String resumeUrl
) {}
