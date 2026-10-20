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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
        assertThat(url).contains("/invite#");
        return url.substring(url.indexOf('#') + 1);
    }

    private ResultActions preview(String token) throws Exception {
        return mvc.perform(post("/api/portal/invites/preview").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"));
    }

    private ResultActions accept(String token, String password) throws Exception {
        return mvc.perform(post("/api/portal/invites/accept").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions claim(String token, User account) throws Exception {
        return mvc.perform(post("/api/portal/invites/claim").header("Authorization", bearer(account))
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"));
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
        preview(token)
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

        preview(first).andExpect(status().isGone()).andExpect(jsonPath("$.message").value(GONE));
        preview(second).andExpect(status().isOk());
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

        preview(token).andExpect(status().isGone()).andExpect(jsonPath("$.message").value(GONE));
        accept(token, "NourPass2026").andExpect(status().isGone());
    }

    @Test
    void revokedInviteIsGone() throws Exception {
        String candidateId = shortlisted();
        String token = rawToken(candidateId);
        mvc.perform(delete(base(candidateId) + "/invites").header("Authorization", hr)).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.message").value("Access link revoked"));

        preview(token).andExpect(status().isGone());
        assertThat(audits("PORTAL_INVITE_REVOKED", candidateId)).isEqualTo(1);
    }

    @Test
    void unknownTokenLooksTheSameAsADeadOne() throws Exception {
        preview("not-a-real-token").andExpect(status().isGone()).andExpect(jsonPath("$.message").value(GONE));
    }

    @Test
    void issueForEmailThatHasStaffAccountIs409() throws Exception {
        issue(candidate(user(Role.HR_INTERVIEWER).getEmail(), "SHORTLISTED"), hr)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This email belongs to a staff account, so it can't get portal access"));
    }

    @Test
    void acceptForEmailThatBecameStaffIsRefused() throws Exception {
        String candidateId = shortlisted();
        String token = rawToken(candidateId);
        User staff = users.save(User.builder().email(email(candidateId)).fullName("Staff").passwordHash(encoder.encode(PASSWORD))
                .role(Role.HR_INTERVIEWER).build());

        accept(token, "NourPass2026").andExpect(status().isGone()).andExpect(jsonPath("$.message").value(GONE));
        assertThat(jdbc.queryForObject("SELECT account_user_id FROM candidates WHERE id = ?", String.class, candidateId)).isNull();
        assertThat(users.findById(staff.getId()).orElseThrow().getRole()).isEqualTo(Role.HR_INTERVIEWER);
    }

    @Test
    void linkStopsWorkingWhenTheCandidateIsNoLongerShortlisted() throws Exception {
        String candidateId = shortlisted();
        String token = rawToken(candidateId);
        jdbc.update("UPDATE candidates SET review_status = 'NONE' WHERE id = ?", candidateId);

        preview(token).andExpect(status().isGone());
        accept(token, "NourPass2026").andExpect(status().isGone());
    }

    @Test
    void someoneWithATemporaryPasswordCanStillOpenALink() throws Exception {
        User pending = user(Role.CANDIDATE);
        pending.resetPassword(pending.getPasswordHash());
        users.save(pending);
        String token = rawToken(shortlisted());

        mvc.perform(post("/api/portal/invites/preview").header("Authorization", bearer(pending))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void acceptWhenTheCandidateAlreadyHasAnAccountAsksThemToSignIn() throws Exception {
        User existing = user(Role.CANDIDATE);
        String token = rawToken(candidate(existing.getEmail(), "SHORTLISTED"));

        preview(token).andExpect(jsonPath("$.data.accountExists").value(true));
        accept(token, "NourPass2026").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You already have an account. Sign in, then open this link again."));
    }

    @Test
    void claimLinksExistingCandidateAccount() throws Exception {
        User existing = user(Role.CANDIDATE);
        String candidateId = candidate(existing.getEmail(), "SHORTLISTED");
        String token = rawToken(candidateId);

        claim(token, existing)
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT account_user_id FROM candidates WHERE id = ?", String.class, candidateId)).isEqualTo(existing.getId());
    }

    @Test
    void claimBySomeoneElseIsRefused() throws Exception {
        String candidateId = shortlisted();
        String token = rawToken(candidateId);

        claim(token, user(Role.CANDIDATE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This link is for a different email. Sign out, then sign in with the email your recruiter has."));
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

    @Test
    void twoAcceptsAtOnceCreateOneAccount() throws Exception {
        String candidateId = shortlisted();
        String token = rawToken(candidateId);

        List<Integer> statuses = race(() -> accept(token, "NourPass2026"), () -> accept(token, "OtherPass2026"));

        assertThat(statuses).containsExactlyInAnyOrder(200, 410);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE lower(email) = lower(?)", Long.class, email(candidateId))).isEqualTo(1);
    }

    @Test
    void newLinkWhileTheCandidateAcceptsSettlesCleanly() throws Exception {
        for (int round = 0; round < 8; round++) {
            String candidateId = shortlisted();
            String token = rawToken(candidateId);

            List<Integer> statuses = race(() -> accept(token, "NourPass2026"), () -> issue(candidateId, hr));

            assertThat(statuses.get(0)).isIn(200, 410);
            assertThat(statuses.get(1)).isIn(201, 409);
            boolean linked = jdbc.queryForObject("SELECT account_user_id IS NOT NULL FROM candidates WHERE id = ?", Boolean.class, candidateId);
            long open = jdbc.queryForObject("SELECT count(*) FROM candidate_invites WHERE candidate_id = ? AND used_at IS NULL AND revoked_at IS NULL",
                    Long.class, candidateId);
            assertThat(open).isEqualTo(linked ? 0L : 1L);
        }
    }

    private interface Call {
        ResultActions run() throws Exception;
    }

    /** Starts both calls together and returns their HTTP statuses in argument order. */
    private static List<Integer> race(Call first, Call second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (Call call : List.of(first, second)) {
                results.add(pool.submit(() -> {
                    start.await();
                    return call.run().andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }
}
