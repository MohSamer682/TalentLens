package com.resumescreener.api.portal.dto;

import com.resumescreener.common.session.InterviewKind;
import com.resumescreener.common.session.InterviewPlace;
import com.resumescreener.common.session.InterviewStatus;

import java.time.Instant;

/** An interview as the candidate sees it: no feedback, rating, notes or interviewer email. */
public record PortalInterview(String id, InterviewKind kind, String interviewerName, String interviewerHeadline, Instant scheduledStart,
                              int durationMinutes, InterviewPlace placeType, String location, InterviewStatus status) {
}
