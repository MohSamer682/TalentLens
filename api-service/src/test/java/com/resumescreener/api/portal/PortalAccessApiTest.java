package com.resumescreener.api.portal;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PortalAccessApiTest extends ApiIntegrationTest {

    static final String GONE = "This link is no longer valid. Ask your recruiter for a new one.";

    @Autowired JdbcTemplate jdbc;

    User recruiter;
    String hr;
    String sessionId;

    @BeforeEach
    void cycle() {
        recruiter = user(Role.RECRUITER);
        hr = bearer(recruiter);
        sessionId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO screening_sessions (id, user_id, title, job_title, status, created_at) VALUES (?, ?, 'Backend 2026', 'Senior Backend Engineer', 'DONE', now())",
                sessionId, recruiter.getId());
    }

    private String candidate(String email, String review) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO candidates (id, session_id, name, email, status, review_status) VALUES (?, ?, 'Nour Hassan', ?, 'SCORED', ?)",
                id, sessionId, email, review);
        return id;
    }

    private String shortlisted() {
        return candidate("nour-" + UUID.randomUUID() + "@mail.io", "SHORTLISTED");
    }

    private String base(String candidateId) {
        return "/api/sessions/" + sessionId + "/candidates/" + candidateId + "/portal";
    }

    private ResultActions issue(String candidateId, String token) throws Exception {
        return mvc.perform(post(base(candidateId) + "/invites").header("Authorization", token));
    }

    private String rawToken(String candidateId) throws Exception {
        String url = JsonPath.read(issue(candidateId, hr).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.url");
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private ResultActions accept(String token, String password) throws Exception {
        return mvc.perform(post("/api/portal/invites/" + token + "/accept").contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + password + "\"}"));
    }

    private long audits(String action, String candidateId) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE action = ? AND target_id = ?", Long.class, action, candidateId);
    }

    private String email(String candidateId) {
        return jdbc.queryForObject("SELECT email FROM candidates WHERE id = ?", String.class, candidateId);
    }

    @Test
    void inviteRequiresShortlistedCandidateWithEmail() throws Exception {
        issue(candidate("a-" + UUID.randomUUID() + "@mail.io", "NONE"), hr)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Shortlist the candidate before giving them portal access"));
        issue(candidate(null, "SHORTLISTED"), hr)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Add the candidate's email first"));
    }

    @Test
    void onlyTheSessionOwnerCanIssue() throws Exception {
        issue(shortlisted(), bearer(user(Role.RECRUITER))).andExpect(status().isForbidden());
    }

    @Test
    void previewShowsTheJobWithoutAccountDetails() throws Exception {
        String token = rawToken(shortlisted());
        mvc.perform(get("/api/portal/invites/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobTitle").value("Senior Backend Engineer"))
                .andExpect(jsonPath("$.data.candidateName").value("Nour Hassan"))
                .andExpect(jsonPath("$.data.accountExists").value(false));
    }

    @Test
    void newInviteRevokesPrevious() throws Exception {
        String candidateId = shortlisted();
        String first = rawToken(candidateId);
        String second = rawToken(candidateId);

        mvc.perform(get("/api/portal/invites/" + first)).andExpect(status().isGone()).andExpect(jsonPath("$.message").value(GONE));
        mvc.perform(get("/api/portal/invites/" + second)).andExpect(status().isOk());
        assertThat(audits("PORTAL_INVITE_ISSUED", candidateId)).isEqualTo(2);
    }

    @Test
    void acceptCreatesCandidateUserLinksAndSignsIn() throws Exception {
        String candidateId = shortlisted();
        String token = rawToken(candidateId);

        String jwt = JsonPath.read(accept(token, "NourPass2026").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value("CANDIDATE"))
                .andReturn().getResponse().getContentAsString(), "$.data.token");

        User account = users.findByEmailIgnoreCase(email(candidateId)).orElseThrow();
        assertThat(account.getRole()).isEqualTo(Role.CANDIDATE);
        assertThat(account.getFullName()).isEqualTo("Nour Hassan");
        assertThat(encoder.matches("NourPass2026", account.getPasswordHash())).isTrue();
        assertThat(jdbc.queryForObject("SELECT account_user_id FROM candidates WHERE id = ?", String.class, candidateId)).isEqualTo(account.getId());
        assertThat(jdbc.queryForObject("SELECT used_at IS NOT NULL FROM candidate_invites WHERE candidate_id = ?", Boolean.class, candidateId)).isTrue();
        assertThat(audits("PORTAL_INVITE_REDEEMED", candidateId)).isEqualTo(1);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions").isEmpty());
    }

    @Test
    void inviteCannotBeRedeemedTwice() throws Exception {
        String token = rawToken(shortlisted());
        accept(token, "NourPass2026").andExpect(status().isOk());
        accept(token, "NourPass2026").andExpect(status().isGone()).andExpect(jsonPath("$.error.code").value("GONE"));
    }

    @Test
    void shortPasswordIsRefused() throws Exception {
        accept(rawToken(shortlisted()), "short").andExpect(status().isBadRequest());
    }

    @Test
    void expiredInviteIsGone() throws Exception {
        String candidateId = shortlisted();
        String token = rawToken(candidateId);
        jdbc.update("UPDATE candidate_invites SET created_at = now() - interval '8 days', expires_at = now() - interval '1 day' WHERE candidate_id = ?", candidateId);

        mvc.perform(get("/api/portal/invites/" + token)).andExpect(status().isGone()).andExpect(jsonPath("$.message").value(GONE));
        accept(token, "NourPass2026").andExpect(status().isGone());
    }

    @Test
    void revokedInviteIsGone() throws Exception {
        String candidateId = shortlisted();
        String token = rawToken(candidateId);
        mvc.perform(delete(base(candidateId) + "/invites").header("Authorization", hr)).andExpect(status().isNoContent());

        mvc.perform(get("/api/portal/invites/" + token)).andExpect(status().isGone());
        assertThat(audits("PORTAL_INVITE_REVOKED", candidateId)).isEqualTo(1);
    }

    @Test
    void unknownTokenLooksTheSameAsADeadOne() throws Exception {
        mvc.perform(get("/api/portal/invites/not-a-real-token")).andExpect(status().isGone()).andExpect(jsonPath("$.message").value(GONE));
    }

    @Test
    void acceptForEmailThatHasStaffAccountIsRefused() throws Exception {
        User staff = user(Role.HR_INTERVIEWER);
        String candidateId = candidate(staff.getEmail(), "SHORTLISTED");
        String token = rawToken(candidateId);

        accept(token, "NourPass2026").andExpect(status().isGone()).andExpect(jsonPath("$.message").value(GONE));
        assertThat(jdbc.queryForObject("SELECT account_user_id FROM candidates WHERE id = ?", String.class, candidateId)).isNull();
        assertThat(users.findById(staff.getId()).orElseThrow().getRole()).isEqualTo(Role.HR_INTERVIEWER);
    }

    @Test
    void acceptWhenTheCandidateAlreadyHasAnAccountAsksThemToSignIn() throws Exception {
        User existing = user(Role.CANDIDATE);
        String token = rawToken(candidate(existing.getEmail(), "SHORTLISTED"));

        mvc.perform(get("/api/portal/invites/" + token)).andExpect(jsonPath("$.data.accountExists").value(true));
        accept(token, "NourPass2026").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You already have an account. Sign in, then open this link again."));
    }

    @Test
    void claimLinksExistingCandidateAccount() throws Exception {
        User existing = user(Role.CANDIDATE);
        String candidateId = candidate(existing.getEmail(), "SHORTLISTED");
        String token = rawToken(candidateId);

        mvc.perform(post("/api/portal/invites/" + token + "/claim").header("Authorization", bearer(existing)))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT account_user_id FROM candidates WHERE id = ?", String.class, candidateId)).isEqualTo(existing.getId());
    }

    @Test
    void claimBySomeoneElseIsRefused() throws Exception {
        String candidateId = shortlisted();
        String token = rawToken(candidateId);

        mvc.perform(post("/api/portal/invites/" + token + "/claim").header("Authorization", bearer(user(Role.CANDIDATE))))
                .andExpect(status().isGone());
        assertThat(jdbc.queryForObject("SELECT account_user_id FROM candidates WHERE id = ?", String.class, candidateId)).isNull();
    }

    @Test
    void createAccountSetsMustChangePassword() throws Exception {
        String candidateId = shortlisted();
        mvc.perform(post(base(candidateId) + "/account").header("Authorization", hr).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"temporaryPassword\":\"TempPass1234\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.account.email").value(email(candidateId)));

        User account = users.findByEmailIgnoreCase(email(candidateId)).orElseThrow();
        assertThat(account.getRole()).isEqualTo(Role.CANDIDATE);
        assertThat(account.isMustChangePassword()).isTrue();
        assertThat(audits("PORTAL_ACCOUNT_CREATED", candidateId)).isEqualTo(1);
    }

    @Test
    void createAccountForExistingEmailIs409() throws Exception {
        String candidateId = candidate(user(Role.RECRUITER).getEmail(), "SHORTLISTED");
        mvc.perform(post(base(candidateId) + "/account").header("Authorization", hr).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"temporaryPassword\":\"TempPass1234\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void accessShowsTheOpenLinkAndTheAccount() throws Exception {
        String candidateId = shortlisted();
        mvc.perform(get(base(candidateId)).header("Authorization", hr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account").doesNotExist())
                .andExpect(jsonPath("$.data.invite").doesNotExist());

        accept(rawToken(candidateId), "NourPass2026");
        mvc.perform(get(base(candidateId)).header("Authorization", hr))
                .andExpect(jsonPath("$.data.account.email").value(email(candidateId)))
                .andExpect(jsonPath("$.data.invite").doesNotExist());
    }
}
