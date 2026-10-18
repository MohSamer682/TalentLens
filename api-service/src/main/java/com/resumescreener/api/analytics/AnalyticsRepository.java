package com.resumescreener.api.analytics;

import com.resumescreener.api.analytics.dto.AnalyticsOverview;
import com.resumescreener.api.analytics.dto.AnalyticsOverview.*;
import com.resumescreener.common.session.SessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class AnalyticsRepository {

    private static final String IN_PERIOD = "s.created_at >= :from AND s.created_at < :to";
    private static final String HOURS_TO_RANKED =
            "percentile_cont(0.5) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (s.completed_at - s.created_at)) / 3600)";

    private final NamedParameterJdbcTemplate jdbc;

    public AnalyticsOverview overview(Instant from, Instant to) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("from", Timestamp.from(from))
                .addValue("to", Timestamp.from(to));

        Duration duration = Duration.between(from, to);
        Instant priorFrom = from.minus(duration);
        MapSqlParameterSource priorP = new MapSqlParameterSource()
                .addValue("from", Timestamp.from(priorFrom))
                .addValue("to", Timestamp.from(from));

        Totals currentTotals = totals(p);
        PriorTotals priorTotals = priorTotals(priorP);
        List<FunnelStep> funnelSteps = funnel(p);
        List<StageWait> stageWaits = stageWaits(p);
        List<Week> weeks = weeks(p);
        List<ScoreBand> scoreBands = scoreBands(p);
        List<InterviewerRow> interviewers = interviewers();
        List<RecruiterRow> recruiters = recruiters(p);

        return new AnalyticsOverview(
                from.atOffset(ZoneOffset.UTC).toLocalDate(),
                to.minusNanos(1).atOffset(ZoneOffset.UTC).toLocalDate(),
                currentTotals,
                priorTotals,
                funnelSteps,
                stageWaits,
                weeks,
                scoreBands,
                interviewers,
                recruiters
        );
    }

    private Totals totals(MapSqlParameterSource p) {
        Map<SessionStatus, Long> byStatus = new EnumMap<>(SessionStatus.class);
        for (SessionStatus s : SessionStatus.values()) {
            byStatus.put(s, 0L);
        }
        jdbc.query("SELECT s.status, count(*) AS n FROM screening_sessions s WHERE " + IN_PERIOD + " GROUP BY s.status",
                p, rs -> {
                    byStatus.put(SessionStatus.valueOf(rs.getString("status")), rs.getLong("n"));
                });
        long sessions = byStatus.values().stream().mapToLong(Long::longValue).sum();

        return jdbc.queryForObject("""
                SELECT count(c.id) AS uploaded,
                       count(c.id) FILTER (WHERE c.status = 'SCORED') AS scored,
                       count(c.id) FILTER (WHERE c.status = 'FAILED') AS unreadable,
                       count(c.id) FILTER (WHERE c.review_status != 'NONE') AS shortlisted,
                       (SELECT count(DISTINCT i.id) FROM candidate_interviews i
                          JOIN candidates c2 ON c2.id = i.candidate_id
                          JOIN screening_sessions s2 ON s2.id = c2.session_id
                         WHERE i.status = 'COMPLETED' AND s2.created_at >= :from AND s2.created_at < :to) AS interviews_completed,
                       (SELECT count(DISTINCT o.id) FROM candidate_offers o
                          JOIN candidates c2 ON c2.id = o.candidate_id
                          JOIN screening_sessions s2 ON s2.id = c2.session_id
                         WHERE o.approved_at IS NOT NULL AND s2.created_at >= :from AND s2.created_at < :to) AS offers_approved,
                       (SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (o.uploaded_at - COALESCE(e.created_at, c2.reviewed_at))) / 86400)
                          FROM candidate_offers o
                          JOIN candidates c2 ON c2.id = o.candidate_id
                          JOIN screening_sessions s2 ON s2.id = c2.session_id
                          LEFT JOIN candidate_stage_events e ON e.candidate_id = c2.id AND e.stage = 'SHORTLISTED'
                         WHERE s2.created_at >= :from AND s2.created_at < :to) AS median_days_shortlist_to_offer,
                       (SELECT %s FROM screening_sessions s
                         WHERE s.status = 'DONE' AND s.completed_at IS NOT NULL AND %s) AS median_hours
                  FROM candidates c JOIN screening_sessions s ON s.id = c.session_id
                 WHERE %s
                """.formatted(HOURS_TO_RANKED, IN_PERIOD, IN_PERIOD), p, (rs, i) -> {
            long scored = rs.getLong("scored");
            long shortlisted = rs.getLong("shortlisted");
            long interviewsCompleted = rs.getLong("interviews_completed");
            long offersApproved = rs.getLong("offers_approved");
            Double medianShortlistToOffer = nullableDouble(rs, "median_days_shortlist_to_offer");

            return new Totals(
                    sessions,
                    byStatus,
                    rs.getLong("uploaded"),
                    scored,
                    rs.getLong("unreadable"),
                    shortlisted,
                    scored == 0 ? null : (double) shortlisted / scored,
                    nullableDouble(rs, "median_hours"),
                    interviewsCompleted,
                    offersApproved,
                    medianShortlistToOffer
            );
        });
    }

    private PriorTotals priorTotals(MapSqlParameterSource p) {
        return jdbc.queryForObject("""
                SELECT count(c.id) FILTER (WHERE c.status = 'SCORED') AS scored,
                       count(c.id) FILTER (WHERE c.review_status != 'NONE') AS shortlisted,
                       (SELECT count(DISTINCT i.id) FROM candidate_interviews i
                          JOIN candidates c2 ON c2.id = i.candidate_id
                          JOIN screening_sessions s2 ON s2.id = c2.session_id
                         WHERE i.status = 'COMPLETED' AND s2.created_at >= :from AND s2.created_at < :to) AS interviews_completed,
                       (SELECT count(DISTINCT o.id) FROM candidate_offers o
                          JOIN candidates c2 ON c2.id = o.candidate_id
                          JOIN screening_sessions s2 ON s2.id = c2.session_id
                         WHERE o.approved_at IS NOT NULL AND s2.created_at >= :from AND s2.created_at < :to) AS offers_approved,
                       (SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (o.uploaded_at - COALESCE(e.created_at, c2.reviewed_at))) / 86400)
                          FROM candidate_offers o
                          JOIN candidates c2 ON c2.id = o.candidate_id
                          JOIN screening_sessions s2 ON s2.id = c2.session_id
                          LEFT JOIN candidate_stage_events e ON e.candidate_id = c2.id AND e.stage = 'SHORTLISTED'
                         WHERE s2.created_at >= :from AND s2.created_at < :to) AS median_days_shortlist_to_offer
                  FROM candidates c JOIN screening_sessions s ON s.id = c.session_id
                 WHERE %s
                """.formatted(IN_PERIOD), p, (rs, i) -> {
            long scored = rs.getLong("scored");
            long shortlisted = rs.getLong("shortlisted");
            Double rate = scored == 0 ? null : (double) shortlisted / scored;
            return new PriorTotals(
                    scored,
                    rate,
                    rs.getLong("interviews_completed"),
                    rs.getLong("offers_approved"),
                    nullableDouble(rs, "median_days_shortlist_to_offer")
            );
        });
    }

    private List<FunnelStep> funnel(MapSqlParameterSource p) {
        return jdbc.queryForObject("""
                SELECT count(c.id) AS uploaded,
                       count(c.id) FILTER (WHERE c.status = 'SCORED') AS scored,
                       count(c.id) FILTER (WHERE c.review_status != 'NONE') AS shortlisted,
                       count(c.id) FILTER (WHERE c.review_status IN ('CONTACTED', 'INTERVIEW_SCHEDULED', 'INTERVIEWED', 'OFFER', 'OFFER_APPROVED')) AS contacted,
                       count(c.id) FILTER (WHERE c.review_status IN ('INTERVIEWED', 'OFFER', 'OFFER_APPROVED')) AS interviewed,
                       count(c.id) FILTER (WHERE c.review_status IN ('OFFER', 'OFFER_APPROVED')) AS offer,
                       count(c.id) FILTER (WHERE c.review_status = 'OFFER_APPROVED') AS approved
                  FROM candidates c JOIN screening_sessions s ON s.id = c.session_id
                 WHERE %s
                """.formatted(IN_PERIOD), p, (rs, i) -> {
            long up = rs.getLong("uploaded");
            long sc = rs.getLong("scored");
            long sl = rs.getLong("shortlisted");
            long co = rs.getLong("contacted");
            long iv = rs.getLong("interviewed");
            long of = rs.getLong("offer");
            long ap = rs.getLong("approved");

            return List.of(
                    new FunnelStep("UPLOADED", "Uploaded", up, null, up - sc),
                    new FunnelStep("SCORED", "Scored", sc, up == 0 ? null : (double) sc / up, sc - sl),
                    new FunnelStep("SHORTLISTED", "Shortlisted", sl, sc == 0 ? null : (double) sl / sc, sl - co),
                    new FunnelStep("CONTACTED", "Contacted", co, sl == 0 ? null : (double) co / sl, co - iv),
                    new FunnelStep("INTERVIEWED", "Interviewed", iv, co == 0 ? null : (double) iv / co, iv - of),
                    new FunnelStep("OFFER", "Offer", of, iv == 0 ? null : (double) of / iv, of - ap),
                    new FunnelStep("APPROVED", "Offer approved", ap, of == 0 ? null : (double) ap / of, 0L)
            );
        });
    }

    private List<StageWait> stageWaits(MapSqlParameterSource p) {
        Double scMed = queryWaitMed(p, "SHORTLISTED", "CONTACTED");
        Double scP90 = queryWaitP90(p, "SHORTLISTED", "CONTACTED");

        Double ciMed = queryWaitMed(p, "CONTACTED", "INTERVIEW_SCHEDULED");
        Double ciP90 = queryWaitP90(p, "CONTACTED", "INTERVIEW_SCHEDULED");

        Double ifMed = queryInterviewFeedbackWaitMed(p);
        Double ifP90 = queryInterviewFeedbackWaitP90(p);

        Double ioMed = queryWaitMed(p, "INTERVIEWED", "OFFER");
        Double ioP90 = queryWaitP90(p, "INTERVIEWED", "OFFER");

        Double oaMed = queryOfferWaitMed(p);
        Double oaP90 = queryOfferWaitP90(p);

        return List.of(
                new StageWait("SHORTLISTED_TO_CONTACTED", "Shortlisted to Contacted", scMed, scP90),
                new StageWait("CONTACTED_TO_INTERVIEW", "Contacted to first interview", ciMed, ciP90),
                new StageWait("INTERVIEW_TO_FEEDBACK", "Interview to feedback", ifMed, ifP90),
                new StageWait("INTERVIEWED_TO_OFFER", "Interviewed to Offer", ioMed, ioP90),
                new StageWait("OFFER_TO_APPROVED", "Offer to Approved", oaMed, oaP90)
        );
    }

    private Double queryWaitMed(MapSqlParameterSource p, String fromStage, String toStage) {
        try {
            return jdbc.queryForObject("""
                    SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (e2.created_at - e1.created_at)) / 86400)
                      FROM candidate_stage_events e1
                      JOIN candidate_stage_events e2 ON e2.candidate_id = e1.candidate_id AND e2.created_at > e1.created_at
                      JOIN candidates c ON c.id = e1.candidate_id
                      JOIN screening_sessions s ON s.id = c.session_id
                     WHERE e1.stage = '%s' AND e2.stage = '%s' AND %s
                    """.formatted(fromStage, toStage, IN_PERIOD), p, Double.class);
        } catch (Exception e) {
            return null;
        }
    }

    private Double queryWaitP90(MapSqlParameterSource p, String fromStage, String toStage) {
        try {
            return jdbc.queryForObject("""
                    SELECT percentile_cont(0.9) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (e2.created_at - e1.created_at)) / 86400)
                      FROM candidate_stage_events e1
                      JOIN candidate_stage_events e2 ON e2.candidate_id = e1.candidate_id AND e2.created_at > e1.created_at
                      JOIN candidates c ON c.id = e1.candidate_id
                      JOIN screening_sessions s ON s.id = c.session_id
                     WHERE e1.stage = '%s' AND e2.stage = '%s' AND %s
                    """.formatted(fromStage, toStage, IN_PERIOD), p, Double.class);
        } catch (Exception e) {
            return null;
        }
    }

    private Double queryInterviewFeedbackWaitMed(MapSqlParameterSource p) {
        try {
            return jdbc.queryForObject("""
                    SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (i.feedback_submitted_at - i.scheduled_start)) / 86400)
                      FROM candidate_interviews i
                      JOIN candidates c ON c.id = i.candidate_id
                      JOIN screening_sessions s ON s.id = c.session_id
                     WHERE i.status = 'COMPLETED' AND i.feedback_submitted_at IS NOT NULL AND %s
                    """.formatted(IN_PERIOD), p, Double.class);
        } catch (Exception e) {
            return null;
        }
    }

    private Double queryInterviewFeedbackWaitP90(MapSqlParameterSource p) {
        try {
            return jdbc.queryForObject("""
                    SELECT percentile_cont(0.9) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (i.feedback_submitted_at - i.scheduled_start)) / 86400)
                      FROM candidate_interviews i
                      JOIN candidates c ON c.id = i.candidate_id
                      JOIN screening_sessions s ON s.id = c.session_id
                     WHERE i.status = 'COMPLETED' AND i.feedback_submitted_at IS NOT NULL AND %s
                    """.formatted(IN_PERIOD), p, Double.class);
        } catch (Exception e) {
            return null;
        }
    }

    private Double queryOfferWaitMed(MapSqlParameterSource p) {
        try {
            return jdbc.queryForObject("""
                    SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (o.approved_at - o.uploaded_at)) / 86400)
                      FROM candidate_offers o
                      JOIN candidates c ON c.id = o.candidate_id
                      JOIN screening_sessions s ON s.id = c.session_id
                     WHERE o.approved_at IS NOT NULL AND %s
                    """.formatted(IN_PERIOD), p, Double.class);
        } catch (Exception e) {
            return null;
        }
    }

    private Double queryOfferWaitP90(MapSqlParameterSource p) {
        try {
            return jdbc.queryForObject("""
                    SELECT percentile_cont(0.9) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (o.approved_at - o.uploaded_at)) / 86400)
                      FROM candidate_offers o
                      JOIN candidates c ON c.id = o.candidate_id
                      JOIN screening_sessions s ON s.id = c.session_id
                     WHERE o.approved_at IS NOT NULL AND %s
                    """.formatted(IN_PERIOD), p, Double.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** Every week of the period is present, with zeros where nothing happened. */
    private List<Week> weeks(MapSqlParameterSource p) {
        return jdbc.query("""
                SELECT w.week_start,
                       count(DISTINCT s.id) AS sessions,
                       count(c.id) FILTER (WHERE c.status = 'SCORED') AS scored,
                       count(c.id) FILTER (WHERE c.review_status != 'NONE') AS shortlisted,
                       (SELECT count(DISTINCT i.id) FROM candidate_interviews i
                          JOIN candidates ci ON ci.id = i.candidate_id
                          JOIN screening_sessions si ON si.id = ci.session_id
                         WHERE i.status = 'COMPLETED'
                           AND date_trunc('week', si.created_at, 'UTC') = w.week_start
                           AND si.created_at >= :from AND si.created_at < :to) AS interviews_completed,
                       (SELECT count(DISTINCT o.id) FROM candidate_offers o
                          JOIN candidates co ON co.id = o.candidate_id
                          JOIN screening_sessions so ON so.id = co.session_id
                         WHERE o.approved_at IS NOT NULL
                           AND date_trunc('week', so.created_at, 'UTC') = w.week_start
                           AND so.created_at >= :from AND so.created_at < :to) AS offers_approved
                  FROM generate_series(date_trunc('week', CAST(:from AS timestamptz), 'UTC'),
                                       CAST(:to AS timestamptz) - interval '1 microsecond', interval '1 week') AS w(week_start)
                  LEFT JOIN screening_sessions s
                         ON date_trunc('week', s.created_at, 'UTC') = w.week_start AND %s
                  LEFT JOIN candidates c ON c.session_id = s.id
                 GROUP BY w.week_start
                 ORDER BY w.week_start
                """.formatted(IN_PERIOD), p, (rs, i) -> new Week(
                rs.getTimestamp("week_start").toInstant().atOffset(ZoneOffset.UTC).toLocalDate(),
                rs.getLong("sessions"),
                rs.getLong("scored"),
                rs.getLong("shortlisted"),
                rs.getLong("interviews_completed"),
                rs.getLong("offers_approved")
        ));
    }

    private List<ScoreBand> scoreBands(MapSqlParameterSource p) {
        return jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE cs.composite_score < 40) AS b1,
                       count(*) FILTER (WHERE cs.composite_score >= 40 AND cs.composite_score < 60) AS b2,
                       count(*) FILTER (WHERE cs.composite_score >= 60 AND cs.composite_score < 75) AS b3,
                       count(*) FILTER (WHERE cs.composite_score >= 75 AND cs.composite_score < 90) AS b4,
                       count(*) FILTER (WHERE cs.composite_score >= 90) AS b5,
                       count(*) FILTER (WHERE cs.composite_score < 40 AND c.review_status != 'NONE') AS b1_sl,
                       count(*) FILTER (WHERE cs.composite_score >= 40 AND cs.composite_score < 60 AND c.review_status != 'NONE') AS b2_sl,
                       count(*) FILTER (WHERE cs.composite_score >= 60 AND cs.composite_score < 75 AND c.review_status != 'NONE') AS b3_sl,
                       count(*) FILTER (WHERE cs.composite_score >= 75 AND cs.composite_score < 90 AND c.review_status != 'NONE') AS b4_sl,
                       count(*) FILTER (WHERE cs.composite_score >= 90 AND c.review_status != 'NONE') AS b5_sl,
                       count(*) FILTER (WHERE cs.composite_score < 40 AND ci.has_interview = 1) AS b1_iv,
                       count(*) FILTER (WHERE cs.composite_score >= 40 AND cs.composite_score < 60 AND ci.has_interview = 1) AS b2_iv,
                       count(*) FILTER (WHERE cs.composite_score >= 60 AND cs.composite_score < 75 AND ci.has_interview = 1) AS b3_iv,
                       count(*) FILTER (WHERE cs.composite_score >= 75 AND cs.composite_score < 90 AND ci.has_interview = 1) AS b4_iv,
                       count(*) FILTER (WHERE cs.composite_score >= 90 AND ci.has_interview = 1) AS b5_iv,
                       count(*) FILTER (WHERE cs.composite_score < 40 AND ci.has_hire = 1) AS b1_hr,
                       count(*) FILTER (WHERE cs.composite_score >= 40 AND cs.composite_score < 60 AND ci.has_hire = 1) AS b2_hr,
                       count(*) FILTER (WHERE cs.composite_score >= 60 AND cs.composite_score < 75 AND ci.has_hire = 1) AS b3_hr,
                       count(*) FILTER (WHERE cs.composite_score >= 75 AND cs.composite_score < 90 AND ci.has_hire = 1) AS b4_hr,
                       count(*) FILTER (WHERE cs.composite_score >= 90 AND ci.has_hire = 1) AS b5_hr
                  FROM candidate_scores cs
                  JOIN candidates c ON c.id = cs.candidate_id
                  JOIN screening_sessions s ON s.id = c.session_id
                  LEFT JOIN (
                      SELECT candidate_id,
                             1 AS has_interview,
                             MAX(CASE WHEN recommendation IN ('STRONG_HIRE', 'HIRE') THEN 1 ELSE 0 END) AS has_hire
                        FROM candidate_interviews
                       WHERE status = 'COMPLETED'
                       GROUP BY candidate_id
                  ) ci ON ci.candidate_id = c.id
                 WHERE c.status = 'SCORED' AND %s
                """.formatted(IN_PERIOD), p, (rs, i) -> {
            long b1 = rs.getLong("b1");
            long b2 = rs.getLong("b2");
            long b3 = rs.getLong("b3");
            long b4 = rs.getLong("b4");
            long b5 = rs.getLong("b5");

            long b1_sl = rs.getLong("b1_sl");
            long b2_sl = rs.getLong("b2_sl");
            long b3_sl = rs.getLong("b3_sl");
            long b4_sl = rs.getLong("b4_sl");
            long b5_sl = rs.getLong("b5_sl");

            long b1_iv = rs.getLong("b1_iv");
            long b2_iv = rs.getLong("b2_iv");
            long b3_iv = rs.getLong("b3_iv");
            long b4_iv = rs.getLong("b4_iv");
            long b5_iv = rs.getLong("b5_iv");

            long b1_hr = rs.getLong("b1_hr");
            long b2_hr = rs.getLong("b2_hr");
            long b3_hr = rs.getLong("b3_hr");
            long b4_hr = rs.getLong("b4_hr");
            long b5_hr = rs.getLong("b5_hr");

            return List.of(
                    new ScoreBand("0–39", b1, b1 == 0 ? null : (double) b1_sl / b1, b1_iv == 0 ? null : (double) b1_hr / b1_iv, b1_iv),
                    new ScoreBand("40–59", b2, b2 == 0 ? null : (double) b2_sl / b2, b2_iv == 0 ? null : (double) b2_hr / b2_iv, b2_iv),
                    new ScoreBand("60–74", b3, b3 == 0 ? null : (double) b3_sl / b3, b3_iv == 0 ? null : (double) b3_hr / b3_iv, b3_iv),
                    new ScoreBand("75–89", b4, b4 == 0 ? null : (double) b4_sl / b4, b4_iv == 0 ? null : (double) b4_hr / b4_iv, b4_iv),
                    new ScoreBand("90–100", b5, b5 == 0 ? null : (double) b5_sl / b5, b5_iv == 0 ? null : (double) b5_hr / b5_iv, b5_iv)
            );
        });
    }

    private List<InterviewerRow> interviewers() {
        try {
            return jdbc.query("""
                    SELECT u.id, u.full_name, u.role,
                           count(i.id) FILTER (WHERE i.status = 'COMPLETED') AS done,
                           percentile_cont(0.5) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (i.feedback_submitted_at - i.scheduled_start)) / 3600) AS median_hours,
                           avg(i.rating) AS mean_rating,
                           count(i.id) FILTER (WHERE i.recommendation = 'STRONG_HIRE') AS strong_hire,
                           count(i.id) FILTER (WHERE i.recommendation = 'HIRE') AS hire,
                           count(i.id) FILTER (WHERE i.recommendation = 'NO_HIRE') AS no_hire,
                           count(i.id) FILTER (WHERE i.recommendation = 'STRONG_NO_HIRE') AS strong_no_hire
                      FROM users u
                      JOIN candidate_interviews i ON i.interviewer_id = u.id
                     GROUP BY u.id, u.full_name, u.role
                     HAVING count(i.id) > 0
                     ORDER BY done DESC, u.full_name
                    """, Map.of(), (rs, i) -> new InterviewerRow(
                    rs.getString("id"),
                    rs.getString("full_name"),
                    rs.getString("role"),
                    rs.getLong("done"),
                    nullableDouble(rs, "median_hours"),
                    nullableDouble(rs, "mean_rating"),
                    rs.getLong("strong_hire"),
                    rs.getLong("hire"),
                    rs.getLong("no_hire"),
                    rs.getLong("strong_no_hire")
            ));
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Inner join on users: a recruiter appears only while they and their sessions exist. */
    private List<RecruiterRow> recruiters(MapSqlParameterSource p) {
        return jdbc.query("""
                WITH medians AS (
                    SELECT s.user_id, %s AS median_hours
                      FROM screening_sessions s
                     WHERE s.status = 'DONE' AND s.completed_at IS NOT NULL AND %s
                     GROUP BY s.user_id)
                SELECT u.id, u.full_name,
                       count(DISTINCT s.id) AS sessions,
                       count(c.id) FILTER (WHERE c.status = 'SCORED') AS scored,
                       count(c.id) FILTER (WHERE c.review_status != 'NONE') AS shortlisted,
                       count(c.id) FILTER (WHERE c.review_status IN ('CONTACTED', 'INTERVIEW_SCHEDULED', 'INTERVIEWED', 'OFFER', 'OFFER_APPROVED')) AS contacted,
                       count(c.id) FILTER (WHERE c.review_status IN ('OFFER', 'OFFER_APPROVED')) AS offers,
                       m.median_hours
                  FROM screening_sessions s
                  JOIN users u ON u.id = s.user_id
                  LEFT JOIN candidates c ON c.session_id = s.id
                  LEFT JOIN medians m ON m.user_id = u.id
                 WHERE %s
                 GROUP BY u.id, u.full_name, m.median_hours
                 ORDER BY sessions DESC, u.full_name
                """.formatted(HOURS_TO_RANKED, IN_PERIOD, IN_PERIOD), p, (rs, i) -> {
            long scored = rs.getLong("scored");
            long shortlisted = rs.getLong("shortlisted");
            long contacted = rs.getLong("contacted");
            long offers = rs.getLong("offers");
            Double rate = shortlisted == 0 ? null : (double) offers / shortlisted;

            return new RecruiterRow(
                    rs.getString("id"),
                    rs.getString("full_name"),
                    rs.getLong("sessions"),
                    scored,
                    shortlisted,
                    nullableDouble(rs, "median_hours"),
                    contacted,
                    offers,
                    rate
            );
        });
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double v = rs.getDouble(column);
        return rs.wasNull() ? null : v;
    }
}
