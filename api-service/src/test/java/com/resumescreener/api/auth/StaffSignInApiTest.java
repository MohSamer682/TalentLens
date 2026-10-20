package com.resumescreener.api.auth;

import com.resumescreener.api.support.ApiIntegrationTest;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StaffSignInApiTest extends ApiIntegrationTest {

    @Autowired JdbcTemplate jdbc;

    private ResultActions signIn(String door, String email, String password) throws Exception {
        return mvc.perform(post(door).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private static final String STAFF = "/api/admins/auth/login";
    private static final String CANDIDATE = "/api/auth/login";

    @Test
    void staffSignsInAtAdminsEndpoint() throws Exception {
        User recruiter = user(Role.RECRUITER);
        signIn(STAFF, recruiter.getEmail(), PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value("RECRUITER"))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    void candidateCannotSignInAsStaff() throws Exception {
        User nour = user(Role.CANDIDATE);
        signIn(STAFF, nour.getEmail(), PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WRONG_PORTAL"))
                .andExpect(jsonPath("$.message").value("This is a candidate account. Sign in at /login."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void staffCannotSignInAsCandidate() throws Exception {
        User recruiter = user(Role.RECRUITER);
        signIn(CANDIDATE, recruiter.getEmail(), PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WRONG_PORTAL"))
                .andExpect(jsonPath("$.message").value("This is a staff account. Sign in at /admins."));
    }

    @Test
    void wrongPasswordIsStill401OnBothDoors() throws Exception {
        User recruiter = user(Role.RECRUITER);
        User nour = user(Role.CANDIDATE);
        for (String door : new String[]{STAFF, CANDIDATE}) {
            signIn(door, recruiter.getEmail(), "wrong-password").andExpect(status().isUnauthorized());
            signIn(door, nour.getEmail(), "wrong-password").andExpect(status().isUnauthorized());
            signIn(door, UUID.randomUUID() + "@nobody.io", "wrong-password").andExpect(status().isUnauthorized());
        }
    }

    @Test
    void tokenAudienceMustMatchRoleKind() throws Exception {
        User nour = user(Role.CANDIDATE);
        String token = bearer(nour);
        mvc.perform(get("/api/auth/me").header("Authorization", token)).andExpect(status().isOk());

        jdbc.update("UPDATE users SET role = 'RECRUITER' WHERE id = ?", nour.getId());
        mvc.perform(get("/api/auth/me").header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void sixthFailedStaffLoginIs429() throws Exception {
        User recruiter = user(Role.RECRUITER);
        for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
            signIn(STAFF, recruiter.getEmail(), "wrong-password").andExpect(status().isUnauthorized());
        }
        signIn(STAFF, recruiter.getEmail().toUpperCase(), PASSWORD).andExpect(status().isTooManyRequests());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE action = 'STAFF_LOGIN_FAILED' AND details = ?",
                Long.class, recruiter.getEmail())).isEqualTo(LoginThrottle.MAX_FAILURES);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE details LIKE '%wrong-password%'", Long.class)).isZero();
    }

    @Test
    void registerAlwaysCreatesCandidate() throws Exception {
        String email = UUID.randomUUID() + "@mail.io";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Nour Hassan\",\"email\":\"" + email + "\",\"password\":\"NourPass2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value("CANDIDATE"));
        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE email = ?", String.class, email)).isEqualTo("CANDIDATE");
    }
}
