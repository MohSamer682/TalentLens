package com.resumescreener.api.portal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumescreener.api.jobs.dto.JobDetailsDto;
import com.resumescreener.api.portal.dto.ApplicationSummary;
import com.resumescreener.api.portal.dto.ApplicationView.Contact;
import com.resumescreener.api.portal.dto.PortalInterview;
import com.resumescreener.api.portal.dto.PortalStage;
import com.resumescreener.common.session.InterviewKind;
import com.resumescreener.common.session.InterviewPlace;
import com.resumescreener.common.session.InterviewStatus;
import com.resumescreener.common.session.ReviewStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Reads the candidate's side of an application straight into portal records. Only the columns a candidate may
 * see are selected, and ownership is part of every query: an application is found only through the caller's
 * own account.
 */
@Repository
@RequiredArgsConstructor
class PortalRepository {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    record Application(String id, ReviewStatus reviewStatus, String interviewPlan, JobDetailsDto job, Contact recruiter) {
    }

    private static final String MINE = """
            FROM candidates c
            JOIN users me ON me.id = c.account_user_id AND lower(me.email) = lower(:email)
            JOIN screening_sessions s ON s.id = c.session_id
            """;

    List<ApplicationSummary> summaries(String email, Instant now, int limit, long offset) {
        String sql = """
                SELECT c.id, s.job_title, s.position_code, c.review_status,
                       (SELECT min(i.scheduled_start) FROM candidate_interviews i
                         WHERE i.candidate_id = c.id AND i.status = 'SCHEDULED' AND i.scheduled_start > :now) AS next_at
                """ + MINE + "ORDER BY c.created_at DESC, c.id LIMIT :limit OFFSET :offset";
        MapSqlParameterSource params = new MapSqlParameterSource("email", email)
                .addValue("now", Timestamp.from(now)).addValue("limit", limit).addValue("offset", offset);
        return jdbc.query(sql, params, (rs, n) -> new ApplicationSummary(rs.getString("id"), rs.getString("job_title"),
                rs.getString("position_code"), PortalStage.of(ReviewStatus.valueOf(rs.getString("review_status"))),
                instant(rs.getTimestamp("next_at"))));
    }

    long count(String email) {
        return jdbc.queryForObject("SELECT count(*) " + MINE, new MapSqlParameterSource("email", email), Long.class);
    }

    Optional<Application> application(String email, String candidateId) {
        String sql = """
                SELECT c.id, c.review_status, c.interview_plan, s.job_title, s.position_code, s.job_description,
                       (SELECT coalesce(json_agg(k.skill ORDER BY k.position), '[]')::text
                          FROM session_required_skills k WHERE k.session_id = s.id) AS skills,
                       owner.full_name AS recruiter_name, p.headline AS recruiter_headline
                """ + MINE + """
                JOIN users owner ON owner.id = s.user_id
                LEFT JOIN user_profiles p ON p.user_id = owner.id
                WHERE c.id = :id
                """;
        MapSqlParameterSource params = new MapSqlParameterSource("email", email).addValue("id", candidateId);
        return jdbc.query(sql, params, (rs, n) -> new Application(rs.getString("id"), ReviewStatus.valueOf(rs.getString("review_status")),
                rs.getString("interview_plan"),
                new JobDetailsDto(rs.getString("job_title"), rs.getString("position_code"), rs.getString("job_description"), skills(rs.getString("skills"))),
                new Contact(rs.getString("recruiter_name"), rs.getString("recruiter_headline")))).stream().findFirst();
    }

    List<PortalInterview> interviews(String candidateId) {
        String sql = """
                SELECT i.id, i.kind, u.full_name, p.headline, i.scheduled_start, i.duration_minutes, i.place_type, i.location, i.status
                FROM candidate_interviews i
                JOIN users u ON u.id = i.interviewer_id
                LEFT JOIN user_profiles p ON p.user_id = u.id
                WHERE i.candidate_id = :id
                ORDER BY i.scheduled_start, i.id
                """;
        return jdbc.query(sql, new MapSqlParameterSource("id", candidateId), (rs, n) -> new PortalInterview(rs.getString("id"),
                InterviewKind.valueOf(rs.getString("kind")), rs.getString("full_name"), rs.getString("headline"),
                instant(rs.getTimestamp("scheduled_start")), rs.getInt("duration_minutes"), InterviewPlace.valueOf(rs.getString("place_type")),
                rs.getString("location"), InterviewStatus.valueOf(rs.getString("status"))));
    }

    private List<String> skills(String text) {
        try {
            return json.readValue(text, STRINGS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unreadable skills list", e);
        }
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
