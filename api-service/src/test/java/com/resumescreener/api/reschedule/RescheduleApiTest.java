package com.resumescreener.api.reschedule;

import com.jayway.jsonpath.JsonPath;
import com.resumescreener.api.support.ApiIntegrationTest;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RescheduleApiTest extends ApiIntegrationTest {

    @Autowired JdbcTemplate jdbc;

    User recruiter;
    User nour;
    String sessionId;
    String candidateId;
    String interviewId;

    @BeforeEach
    void seed() {
        recruiter = user(Role.RECRUITER);
        nour = user(Role.CANDIDATE);
        sessionId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO screening_sessions (id, user_id, title, job_title, status, created_at) VALUES (?, ?, 'Backend 2026', 'Senior Backend Engineer', 'DONE', now())",
                sessionId, recruiter.getId());
        candidateId = application(nour);
        interviewId = interview(candidateId, inDays(2), "SCHEDULED");
    }

    private String application(User account) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO candidates (id, session_id, name, email, status, review_status, interview_plan, account_user_id, created_at) "
                + "VALUES (?, ?, 'Nour Hassan', ?, 'SCORED', 'INTERVIEW_SCHEDULED', 'HR', ?, now())", id, sessionId, account.getEmail(), account.getId());
        return id;
    }

    private String interview(String candidate, Instant start, String state) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO candidate_interviews (id, candidate_id, kind, interviewer_id, scheduled_start, status, created_by) VALUES (?, ?, 'HR', ?, ?, ?, ?)",
                id, candidate, recruiter.getId(), Timestamp.from(start), state, recruiter.getId());
        return id;
    }

    private static Instant inDays(int days) {
        return Instant.now().plus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
    }

    private ResultActions ask(String interview, User as, String reason, Instant... slots) throws Exception {
        StringBuilder times = new StringBuilder();
        for (Instant slot : slots) times.append(times.isEmpty() ? "" : ",").append('"').append(slot).append('"');
        return mvc.perform(post("/api/portal/interviews/" + interview + "/reschedule-requests").header("Authorization", bearer(as))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + reason + "\",\"proposedStarts\":[" + times + "]}"));
    }

    private String requestId() throws Exception {
        return JsonPath.read(ask(interviewId, nour, "Exam that morning", inDays(4), inDays(5)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.data.id");
    }

    private String hrBase() {
        return "/api/sessions/" + sessionId + "/candidates/" + candidateId + "/reschedule-requests";
    }

    private ResultActions accept(String requestId, Instant start, long version, User as) throws Exception {
        return mvc.perform(post(hrBase() + "/" + requestId + "/accept").header("Authorization", bearer(as)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"scheduledStart\":\"" + start + "\",\"version\":" + version + "}"));
    }

    private ResultActions decline(String requestId, String note, long version) throws Exception {
        return mvc.perform(post(hrBase() + "/" + requestId + "/decline").header("Authorization", bearer(recruiter)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"" + note + "\",\"version\":" + version + "}"));
    }

    private String stateOf(String requestId) {
        return jdbc.queryForObject("SELECT status FROM interview_reschedule_requests WHERE id = ?", String.class, requestId);
    }

    @Test
    void requestIsSavedWithItsTimes() throws Exception {
        String id = requestId();

        assertThat(stateOf(id)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM interview_reschedule_slots WHERE request_id = ?", Long.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE action = 'RESCHEDULE_REQUESTED' AND target_id = ?", Long.class, id)).isEqualTo(1);
    }

    @Test
    void cannotRequestAfterStart() throws Exception {
        String past = interview(candidateId, inDays(-1), "SCHEDULED");
        ask(past, nour, "Too late", inDays(3)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This interview has already started"));
    }

    @Test
    void cannotRequestForCancelledInterview() throws Exception {
        String cancelled = interview(candidateId, inDays(3), "CANCELLED");
        ask(cancelled, nour, "Moving", inDays(4)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only a scheduled interview can be moved"));
    }

    @Test
    void cannotRequestForSomeoneElsesInterview() throws Exception {
        String theirs = UUID.randomUUID().toString();
        ask(interviewId, user(Role.CANDIDATE), "Not mine", inDays(4)).andExpect(status().isNotFound());
        ask(theirs, nour, "Unknown", inDays(4)).andExpect(status().isNotFound());
    }

    @Test
    void proposedTimesMustBeFutureDistinctAndSoon() throws Exception {
        ask(interviewId, nour, "Exam", inDays(-1)).andExpect(status().isBadRequest());
        ask(interviewId, nour, "Exam", inDays(4), inDays(4)).andExpect(status().isBadRequest());
        ask(interviewId, nour, "Exam", inDays(61)).andExpect(status().isBadRequest());
        ask(interviewId, nour, "Exam").andExpect(status().isBadRequest());
        ask(interviewId, nour, "Exam", inDays(3), inDays(4), inDays(5), inDays(6)).andExpect(status().isBadRequest());
        ask(interviewId, nour, " ", inDays(3)).andExpect(status().isBadRequest());
    }

    @Test
    void secondRequestWhileOneIsPendingIs409() throws Exception {
        requestId();
        ask(interviewId, nour, "Again", inDays(6)).andExpect(status().isConflict());
    }

    @Test
    void acceptMovesInterviewAndClosesRequest() throws Exception {
        String id = requestId();
        Instant slot = inDays(5);

        accept(id, slot, 0, recruiter).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        assertThat(jdbc.queryForObject("SELECT scheduled_start FROM candidate_interviews WHERE id = ?", Timestamp.class, interviewId).toInstant()).isEqualTo(slot);
        assertThat(stateOf(id)).isEqualTo("ACCEPTED");
        assertThat(jdbc.queryForObject("SELECT resolved_by FROM interview_reschedule_requests WHERE id = ?", String.class, id)).isEqualTo(recruiter.getId());
    }

    @Test
    void acceptOfAnsweredRequestIs409() throws Exception {
        String id = requestId();
        decline(id, "No other slot this week", 0).andExpect(status().isOk());

        accept(id, inDays(5), 1, recruiter).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This request was already answered"));
        assertThat(stateOf(id)).isEqualTo("DECLINED");
    }

    @Test
    void staleVersionIs409() throws Exception {
        String id = requestId();
        jdbc.update("UPDATE interview_reschedule_requests SET version = version + 1 WHERE id = ?", id);
        Instant before = jdbc.queryForObject("SELECT scheduled_start FROM candidate_interviews WHERE id = ?", Timestamp.class, interviewId).toInstant();

        accept(id, inDays(5), 0, recruiter).andExpect(status().isConflict());
        assertThat(stateOf(id)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT scheduled_start FROM candidate_interviews WHERE id = ?", Timestamp.class, interviewId).toInstant()).isEqualTo(before);
    }

    @Test
    void declineRequiresNote() throws Exception {
        String id = requestId();
        decline(id, " ", 0).andExpect(status().isBadRequest());
        decline(id, "Panel is fully booked", 0).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT resolution_note FROM interview_reschedule_requests WHERE id = ?", String.class, id)).isEqualTo("Panel is fully booked");
    }

    @Test
    void onlyTheSessionOwnerCanAnswer() throws Exception {
        accept(requestId(), inDays(5), 0, user(Role.RECRUITER)).andExpect(status().isForbidden());
    }

    @Test
    void withdrawOnlyByRequester() throws Exception {
        String id = requestId();
        mvc.perform(post("/api/portal/reschedule-requests/" + id + "/withdraw").header("Authorization", bearer(user(Role.CANDIDATE))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/portal/reschedule-requests/" + id + "/withdraw").header("Authorization", bearer(nour)))
                .andExpect(status().isOk());
        assertThat(stateOf(id)).isEqualTo("WITHDRAWN");
    }

    @Test
    void hrSeesTheRequestOnTheCandidate() throws Exception {
        requestId();
        mvc.perform(get(hrBase()).header("Authorization", bearer(recruiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].reason").value("Exam that morning"))
                .andExpect(jsonPath("$.data[0].proposedStarts.length()").value(2))
                .andExpect(jsonPath("$.data[0].requestedByName").value(nour.getFullName()))
                .andExpect(jsonPath("$.data[0].version").value(0));
    }

    @Test
    void portalViewShowsTheLatestRequest() throws Exception {
        String id = requestId();
        mvc.perform(get("/api/portal/applications/" + candidateId).header("Authorization", bearer(nour)))
                .andExpect(jsonPath("$.data.interviews[0].reschedule.status").value("PENDING"));

        Instant slot = inDays(5);
        accept(id, slot, 0, recruiter);
        mvc.perform(get("/api/portal/applications/" + candidateId).header("Authorization", bearer(nour)))
                .andExpect(jsonPath("$.data.interviews[0].reschedule.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.interviews[0].scheduledStart").value(slot.toString()));
    }

    @Test
    void pipelineFlagsAWaitingRequest() throws Exception {
        requestId();
        mvc.perform(get("/api/pipeline").header("Authorization", bearer(recruiter)))
                .andExpect(jsonPath("$.data[?(@.id == '" + candidateId + "')].rescheduleRequested").value(true));
    }

    @Test
    void acceptRefusesATimeInThePast() throws Exception {
        String id = requestId();
        accept(id, inDays(-1), 0, recruiter).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Pick a time in the future"));
        assertThat(stateOf(id)).isEqualTo("PENDING");
    }

    @Test
    void acceptOnACancelledInterviewLeavesItCancelled() throws Exception {
        String id = requestId();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                "/api/sessions/" + sessionId + "/candidates/" + candidateId + "/interviews/" + interviewId).header("Authorization", bearer(recruiter)))
                .andExpect(status().isOk());

        accept(id, inDays(5), 0, recruiter).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT status FROM candidate_interviews WHERE id = ?", String.class, interviewId)).isEqualTo("CANCELLED");
        assertThat(stateOf(id)).isEqualTo("PENDING");
    }

    @Test
    void pipelineFlagClearsOnceTheInterviewIsCancelled() throws Exception {
        requestId();
        jdbc.update("UPDATE candidate_interviews SET status = 'CANCELLED' WHERE id = ?", interviewId);
        mvc.perform(get("/api/pipeline").header("Authorization", bearer(recruiter)))
                .andExpect(jsonPath("$.data[?(@.id == '" + candidateId + "')].rescheduleRequested").value(false));
    }

    @Test
    void answeringARequestFromAnotherCandidateIs404() throws Exception {
        String id = requestId();
        String other = application(user(Role.CANDIDATE));
        mvc.perform(post("/api/sessions/" + sessionId + "/candidates/" + other + "/reschedule-requests/" + id + "/accept")
                        .header("Authorization", bearer(recruiter)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledStart\":\"" + inDays(5) + "\",\"version\":0}"))
                .andExpect(status().isNotFound());
    }
}
