package com.resumescreener.api.analytics.dto;

import com.resumescreener.common.session.SessionStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Everything counts sessions created in the period, and the resumes inside those sessions. */
public record AnalyticsOverview(LocalDate from, LocalDate to, Totals totals, List<Week> weeks,
                                List<ScoreBand> scoreBands, List<RecruiterRow> recruiters) {

    public record Totals(long sessions, Map<SessionStatus, Long> sessionsByStatus, long resumesUploaded,
                         long resumesScored, long resumesUnreadable, long shortlisted,
                         Double shortlistRate, Double medianHoursToRanked) {}

    /** Activity grouped by the Monday (UTC) of the week each session was created. */
    public record Week(LocalDate weekStart, long sessionsCreated, long resumesScored, long shortlisted) {}

    public record ScoreBand(String label, long count) {}

    public record RecruiterRow(String userId, String fullName, long sessions, long resumesScored,
                               long shortlisted, Double medianHoursToRanked) {}
}
