package com.resumescreener.api.analytics.dto;

import com.resumescreener.common.session.SessionStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Everything counts sessions created in the period, and the resumes inside those sessions. */
public record AnalyticsOverview(
        LocalDate from,
        LocalDate to,
        Totals totals,
        PriorTotals priorTotals,
        List<FunnelStep> funnel,
        List<StageWait> stageWaits,
        List<Week> weeks,
        List<ScoreBand> scoreBands,
        List<InterviewerRow> interviewers,
        List<RecruiterRow> recruiters
) {

    public AnalyticsOverview(LocalDate from, LocalDate to, Totals totals, List<Week> weeks,
                             List<ScoreBand> scoreBands, List<RecruiterRow> recruiters) {
        this(from, to, totals, null, List.of(), List.of(), weeks, scoreBands, List.of(), recruiters);
    }

    public record Totals(
            long sessions,
            Map<SessionStatus, Long> sessionsByStatus,
            long resumesUploaded,
            long resumesScored,
            long resumesUnreadable,
            long shortlisted,
            Double shortlistRate,
            Double medianHoursToRanked,
            long interviewsCompleted,
            long offersApproved,
            Double medianDaysShortlistToOffer
    ) {
        public Totals(long sessions, Map<SessionStatus, Long> sessionsByStatus, long resumesUploaded,
                      long resumesScored, long resumesUnreadable, long shortlisted,
                      Double shortlistRate, Double medianHoursToRanked) {
            this(sessions, sessionsByStatus, resumesUploaded, resumesScored, resumesUnreadable,
                    shortlisted, shortlistRate, medianHoursToRanked, 0L, 0L, null);
        }
    }

    public record PriorTotals(
            long resumesScored,
            Double shortlistRate,
            long interviewsCompleted,
            long offersApproved,
            Double medianDaysShortlistToOffer
    ) {}

    public record FunnelStep(
            String stage,
            String label,
            long count,
            Double conversionRate,
            long dropOff
    ) {}

    public record StageWait(
            String stage,
            String label,
            Double medianDays,
            Double p90Days
    ) {}

    /** Activity grouped by the Monday (UTC) of the week each session was created. */
    public record Week(
            LocalDate weekStart,
            long sessionsCreated,
            long resumesScored,
            long shortlisted,
            long interviewsCompleted,
            long offersApproved
    ) {
        public Week(LocalDate weekStart, long sessionsCreated, long resumesScored, long shortlisted) {
            this(weekStart, sessionsCreated, resumesScored, shortlisted, 0L, 0L);
        }
    }

    public record ScoreBand(
            String label,
            long count,
            Double shortlistRate,
            Double hireRate,
            long interviewed
    ) {
        public ScoreBand(String label, long count) {
            this(label, count, null, null, 0L);
        }
    }

    public record InterviewerRow(
            String userId,
            String fullName,
            String role,
            long interviewsDone,
            Double medianHoursToFeedback,
            Double meanRating,
            long strongHireCount,
            long hireCount,
            long noHireCount,
            long strongNoHireCount
    ) {}

    public record RecruiterRow(
            String userId,
            String fullName,
            long sessions,
            long resumesScored,
            long shortlisted,
            Double medianHoursToRanked,
            long contacted,
            long offers,
            Double shortlistToOfferRate
    ) {
        public RecruiterRow(String userId, String fullName, long sessions, long resumesScored,
                            long shortlisted, Double medianHoursToRanked) {
            this(userId, fullName, sessions, resumesScored, shortlisted, medianHoursToRanked, 0L, 0L, null);
        }
    }
}
