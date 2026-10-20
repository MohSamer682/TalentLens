package com.resumescreener.api.profile;

import com.resumescreener.api.support.ApiIntegrationTest;
import com.resumescreener.common.audit.AuditEventRepository;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserProfileRepository;
import com.resumescreener.common.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminUserApiTest extends ApiIntegrationTest {

    @Autowired UserProfileRepository profiles;
    @Autowired AuditEventRepository audit;
    @Autowired JdbcTemplate jdbc;

    User admin;
    String token;

    @BeforeEach
    void signInAsAdmin() {
        admin = user(Role.ADMIN);
        token = bearer(admin);
    }

    private String json(String s) {
        return s.replace('\'', '"');
    }

    private long auditCount(String action, String targetId) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE action = ? AND target_id = ?", Long.class, action, targetId);
    }

    @Test
    void createMakesAStaffUserWithAProfileAndATemporaryPassword() throws Exception {
        String email = "new-" + UUID.randomUUID() + "@t.io";
        String id = com.jayway.jsonpath.JsonPath.read(mvc.perform(post("/api/admin/users").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'" + email + "','fullName':'Sara Adel','role':'TECHNICAL_INTERVIEWER',"
                                + "'temporaryPassword':'TempPass1234','headline':'Senior Backend Engineer'}")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/admin/users/")))
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andReturn().getResponse().getContentAsString(), "$.data.id");

        User saved = users.findById(id).orElseThrow();
        assertThat(saved.getRole()).isEqualTo(Role.TECHNICAL_INTERVIEWER);
        assertThat(encoder.matches("TempPass1234", saved.getPasswordHash())).isTrue();
        assertThat(profiles.findById(id).orElseThrow().getHeadline()).isEqualTo("Senior Backend Engineer");
        assertThat(auditCount("USER_CREATE", id)).isEqualTo(1);
    }

    @Test
    void createAcceptsABlankPhone() throws Exception {
        mvc.perform(post("/api/admin/users").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'blank-" + UUID.randomUUID() + "@t.io','fullName':'No Phone','role':'RECRUITER',"
                                + "'temporaryPassword':'TempPass1234','phone':''}")))
                .andExpect(status().isCreated());
    }

    @Test
    void emailIsUniqueIgnoringCaseWith409() throws Exception {
        User existing = user(Role.RECRUITER);
        mvc.perform(post("/api/admin/users").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'" + existing.getEmail().toUpperCase() + "','fullName':'X','role':'RECRUITER','temporaryPassword':'TempPass1234'}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void updateEditsNameRoleAndProfileAndRejectsStaleVersion() throws Exception {
        User target = user(Role.RECRUITER);
        String body = json("{'fullName':'Karim Adel','email':'" + target.getEmail() + "','role':'HR_CHIEF','headline':'Lead','version':0}");
        mvc.perform(put("/api/admin/users/" + target.getId()).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("HR_CHIEF"))
                .andExpect(jsonPath("$.data.headline").value("Lead"));

        mvc.perform(put("/api/admin/users/" + target.getId()).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        assertThat(users.findById(target.getId()).orElseThrow().getRole()).isEqualTo(Role.HR_CHIEF);
        assertThat(auditCount("USER_UPDATE", target.getId())).isEqualTo(1);
    }

    @Test
    void hideAndUnhide() throws Exception {
        User target = user(Role.TECHNICAL_INTERVIEWER);
        mvc.perform(patch("/api/admin/users/" + target.getId() + "/status").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json("{'status':'HIDDEN'}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HIDDEN"));
        assertThat(users.findById(target.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.HIDDEN);

        mvc.perform(get("/api/interviewers").header("Authorization", token))
                .andExpect(jsonPath("$.data[?(@.id == '" + target.getId() + "')]").isEmpty());

        mvc.perform(patch("/api/admin/users/" + target.getId() + "/status").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json("{'status':'ACTIVE'}")))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        assertThat(auditCount("USER_HIDE", target.getId())).isEqualTo(1);
        assertThat(auditCount("USER_UNHIDE", target.getId())).isEqualTo(1);
    }

    @Test
    void cannotHideOrDeleteYourself() throws Exception {
        mvc.perform(patch("/api/admin/users/" + admin.getId() + "/status").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json("{'status':'HIDDEN'}")))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/admin/users/" + admin.getId()).header("Authorization", token))
                .andExpect(status().isBadRequest());
        assertThat(users.findById(admin.getId())).isPresent();
    }

    @Test
    void deleteWithHistoryReturns409WithHideHint() throws Exception {
        User target = user(Role.RECRUITER);
        jdbc.update("INSERT INTO screening_sessions (id, user_id, title, job_title, status, created_at) VALUES (?, ?, 't', 't', 'DONE', now())",
                UUID.randomUUID().toString(), target.getId());

        mvc.perform(delete("/api/admin/users/" + target.getId()).header("Authorization", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This account has history (sessions, interviews or notes), so it can't be deleted. Hide it instead."));
        assertThat(users.findById(target.getId())).isPresent();
    }

    @Test
    void deleteWithoutHistoryRemovesUserAndProfile() throws Exception {
        User target = user(Role.RECRUITER);
        mvc.perform(put("/api/admin/users/" + target.getId()).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(json("{'fullName':'Gone Soon','email':'" + target.getEmail() + "','role':'RECRUITER','bio':'x','version':0}")))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/admin/users/" + target.getId()).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));

        assertThat(users.findById(target.getId())).isEmpty();
        assertThat(profiles.findById(target.getId())).isEmpty();
        assertThat(auditCount("USER_DELETE", target.getId())).isEqualTo(1);
    }

    @Test
    void auditLogStillNamesADeletedUser() throws Exception {
        User target = user(Role.RECRUITER);
        mvc.perform(put("/api/me/profile").header("Authorization", bearer(target)).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'fullName':'Short Stay','version':0}")))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/admin/users/" + target.getId()).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));

        assertThat(jdbc.queryForObject("SELECT actor_email FROM audit_events WHERE action = 'PROFILE_UPDATE' AND target_id = ?",
                String.class, target.getId())).isEqualTo(target.getEmail());
    }

    @Test
    void resetPasswordSetsTemporaryPassword() throws Exception {
        User target = user(Role.RECRUITER);
        mvc.perform(post("/api/admin/users/" + target.getId() + "/password").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json("{'temporaryPassword':'ResetPass999'}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andExpect(jsonPath("$.data.version").value(1));
        User saved = users.findById(target.getId()).orElseThrow();
        assertThat(saved.isMustChangePassword()).isTrue();
        assertThat(encoder.matches("ResetPass999", saved.getPasswordHash())).isTrue();
        assertThat(auditCount("PASSWORD_RESET", target.getId())).isEqualTo(1);
    }

    @Test
    void listFiltersByStatusRoleAndQueryAndCapsSize() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        User a = users.save(User.builder().email("a-" + tag + "@t.io").fullName("Alpha " + tag).passwordHash("h").role(Role.RECRUITER).build());
        User b = users.save(User.builder().email("b-" + tag + "@t.io").fullName("Beta " + tag).passwordHash("h").role(Role.HR_CHIEF).build());
        b.hide();
        users.save(b);

        mvc.perform(get("/api/admin/users").param("q", tag.toUpperCase()).header("Authorization", token))
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mvc.perform(get("/api/admin/users").param("q", tag).param("status", "HIDDEN").header("Authorization", token))
                .andExpect(jsonPath("$.data.content[0].id").value(b.getId()))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/admin/users").param("q", tag).param("role", "RECRUITER").header("Authorization", token))
                .andExpect(jsonPath("$.data.content[0].id").value(a.getId()));
        mvc.perform(get("/api/admin/users").param("size", "500").header("Authorization", token))
                .andExpect(jsonPath("$.data.size").value(100));
    }

    @Test
    void searchTreatsWildcardsAsPlainText() throws Exception {
        user(Role.RECRUITER);
        mvc.perform(get("/api/admin/users").param("q", "%").header("Authorization", token))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void cannotAssignCandidateRoleToStaff() throws Exception {
        mvc.perform(post("/api/admin/users").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'c-" + UUID.randomUUID() + "@t.io','fullName':'C','role':'CANDIDATE','temporaryPassword':'TempPass1234'}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Candidate and staff accounts can't switch roles"));

        User staff = user(Role.RECRUITER);
        mvc.perform(patch("/api/admin/users/" + staff.getId() + "/role").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json("{'role':'CANDIDATE'}")))
                .andExpect(status().isBadRequest());

        User candidate = user(Role.CANDIDATE);
        mvc.perform(put("/api/admin/users/" + candidate.getId()).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'fullName':'C','email':'" + candidate.getEmail() + "','role':'RECRUITER','version':0}")))
                .andExpect(status().isBadRequest());
        assertThat(users.findById(candidate.getId()).orElseThrow().getRole()).isEqualTo(Role.CANDIDATE);
        assertThat(users.findById(staff.getId()).orElseThrow().getRole()).isEqualTo(Role.RECRUITER);
    }

    @Test
    void nonAdminIsRefused() throws Exception {
        mvc.perform(get("/api/admin/users").header("Authorization", bearer(user(Role.RECRUITER))))
                .andExpect(status().isForbidden());
    }
}
