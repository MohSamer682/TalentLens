package com.resumescreener.api.analytics;

import com.resumescreener.api.analytics.dto.AnalyticsOverview;
import com.resumescreener.api.analytics.dto.AnalyticsOverview.RecruiterRow;
import com.resumescreener.api.analytics.dto.AnalyticsOverview.ScoreBand;
import com.resumescreener.api.analytics.dto.AnalyticsOverview.Totals;
import com.resumescreener.api.analytics.dto.AnalyticsOverview.Week;
import com.resumescreener.common.session.SessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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
        return new AnalyticsOverview(from.atOffset(ZoneOffset.UTC).toLocalDate(),
                to.minusNanos(1).atOffset(ZoneOffset.UTC).toLocalDate(),
                totals(p), weeks(p), scoreBands(p), recruiters(p));
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
                       count(c.id) FILTER (WHERE c.review_status = 'SHORTLISTED') AS shortlisted,
                       (SELECT %s FROM screening_sessions s
                         WHERE s.status = 'DONE' AND s.completed_at IS NOT NULL AND %s) AS median_hours
                  FROM candidates c JOIN screening_sessions s ON s.id = c.session_id
                 WHERE %s
                """.formatted(HOURS_TO_RANKED, IN_PERIOD, IN_PERIOD), p, (rs, i) -> {
            long scored = rs.getLong("scored");
            long shortlisted = rs.getLong("shortlisted");
            return new Totals(sessions, byStatus, rs.getLong("uploaded"), scored, rs.getLong("unreadable"),
                    shortlisted, scored == 0 ? null : (double) shortlisted / scored, nullableDouble(rs, "median_hours"));
        });
    }

    /** Every week of the period is present, with zeros where nothing happened. */
    private List<Week> weeks(MapSqlParameterSource p) {
        return jdbc.query("""
                SELECT w.week_start,
                       count(DISTINCT s.id) AS sessions,
                       count(c.id) FILTER (WHERE c.status = 'SCORED') AS scored,
                       count(c.id) FILTER (WHERE c.review_status = 'SHORTLISTED') AS shortlisted
                  FROM generate_series(date_trunc('week', CAST(:from AS timestamptz), 'UTC'),
                                       CAST(:to AS timestamptz) - interval '1 microsecond', interval '1 week') AS w(week_start)
                  LEFT JOIN screening_sessions s
                         ON date_trunc('week', s.created_at, 'UTC') = w.week_start AND %s
                  LEFT JOIN candidates c ON c.session_id = s.id
                 GROUP BY w.week_start
                 ORDER BY w.week_start
                """.formatted(IN_PERIOD), p, (rs, i) -> new Week(
                rs.getTimestamp("week_start").toInstant().atOffset(ZoneOffset.UTC).toLocalDate(),
                rs.getLong("sessions"), rs.getLong("scored"), rs.getLong("shortlisted")));
    }

    private List<ScoreBand> scoreBands(MapSqlParameterSource p) {
        return jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE cs.composite_score < 40) AS b1,
                       count(*) FILTER (WHERE cs.composite_score >= 40 AND cs.composite_score < 60) AS b2,
                       count(*) FILTER (WHERE cs.composite_score >= 60 AND cs.composite_score < 75) AS b3,
                       count(*) FILTER (WHERE cs.composite_score >= 75 AND cs.composite_score < 90) AS b4,
                       count(*) FILTER (WHERE cs.composite_score >= 90) AS b5
                  FROM candidate_scores cs
                  JOIN candidates c ON c.id = cs.candidate_id
                  JOIN screening_sessions s ON s.id = c.session_id
                 WHERE c.status = 'SCORED' AND %s
                """.formatted(IN_PERIOD), p, (rs, i) -> List.of(
                new ScoreBand("0–39", rs.getLong("b1")),
                new ScoreBand("40–59", rs.getLong("b2")),
                new ScoreBand("60–74", rs.getLong("b3")),
                new ScoreBand("75–89", rs.getLong("b4")),
                new ScoreBand("90–100", rs.getLong("b5"))));
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
                       count(c.id) FILTER (WHERE c.review_status = 'SHORTLISTED') AS shortlisted,
                       m.median_hours
                  FROM screening_sessions s
                  JOIN users u ON u.id = s.user_id
                  LEFT JOIN candidates c ON c.session_id = s.id
                  LEFT JOIN medians m ON m.user_id = u.id
                 WHERE %s
                 GROUP BY u.id, u.full_name, m.median_hours
                 ORDER BY sessions DESC, u.full_name
                """.formatted(HOURS_TO_RANKED, IN_PERIOD, IN_PERIOD), p, (rs, i) -> new RecruiterRow(
                rs.getString("id"), rs.getString("full_name"), rs.getLong("sessions"), rs.getLong("scored"),
                rs.getLong("shortlisted"), nullableDouble(rs, "median_hours")));
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double v = rs.getDouble(column);
        return rs.wasNull() ? null : v;
    }
}
