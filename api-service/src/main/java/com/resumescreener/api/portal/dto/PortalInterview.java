package com.resumescreener.api.portal.dto;

import com.resumescreener.common.session.InterviewKind;
import com.resumescreener.common.session.InterviewPlace;
import com.resumescreener.common.session.InterviewStatus;

import com.resumescreener.common.reschedule.RescheduleStatus;

import java.time.Instant;
import java.util.List;

/**
 * An interview as the candidate sees it: no feedback, rating, notes or interviewer email. {@code reschedule} is
 * the candidate's latest request to move it, if any.
 */
public record PortalInterview(String id, InterviewKind kind, String interviewerName, String interviewerHeadline, Instant scheduledStart,
                              int durationMinutes, InterviewPlace placeType, String location, InterviewStatus status, Reschedule reschedule) {

    public record Reschedule(String id, RescheduleStatus status, List<Instant> proposedStarts, String resolutionNote, Instant createdAt) {
    }
}
