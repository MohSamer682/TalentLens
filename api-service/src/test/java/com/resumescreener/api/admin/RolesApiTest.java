package com.resumescreener.api.admin;

import com.jayway.jsonpath.JsonPath;
import com.resumescreener.api.support.ApiIntegrationTest;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RolesApiTest extends ApiIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired RoleController controller;

    User admin;
    List<Map<String, Object>> rolesBefore;
    List<Map<String, Object>> grantsBefore;

    @BeforeEach
    void seed() {
        admin = user(Role.ADMIN);
        rolesBefore = jdbc.queryForList("SELECT code, name, description FROM roles");
        grantsBefore = jdbc.queryForList("SELECT role, permission FROM role_permissions");
    }

    /** Roles are shared by every test on this database, so put names and grants back. */
    @AfterEach
    void restore() {
        rolesBefore.forEach(r -> jdbc.update("UPDATE roles SET name = ?, description = ? WHERE code = ?", r.get("name") + "~", r.get("description"), r.get("code")));
        rolesBefore.forEach(r -> jdbc.update("UPDATE roles SET name = ? WHERE code = ?", r.get("name"), r.get("code")));
        jdbc.update("DELETE FROM role_permissions");
        grantsBefore.forEach(g -> jdbc.update("INSERT INTO role_permissions (role, permission) VALUES (?, ?)", g.get("role"), g.get("permission")));
        SecurityContextHolder.clearContext();
    }

    private long version(Role role) {
        return jdbc.queryForObject("SELECT version FROM roles WHERE code = ?", Long.class, role.name());
    }

    private ResultActions rename(Role role, String name, String description, long version) throws Exception {
        return mvc.perform(patch("/api/admin/roles/" + role).header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"description\":\"" + description + "\",\"version\":" + version + "}"));
    }

    private ResultActions putPermissions(String role, String permissions, long version) throws Exception {
        return mvc.perform(put("/api/admin/roles/" + role + "/permissions").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"permissions\":" + permissions + ",\"version\":" + version + "}"));
    }

    private ResultActions move(Role role, User as, String... ids) throws Exception {
        String body = "{\"userIds\":[" + String.join(",", java.util.Arrays.stream(ids).map(i -> "\"" + i + "\"").toList()) + "]}";
        return mvc.perform(post("/api/admin/roles/" + role + "/members").header("Authorization", bearer(as))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String roleOf(User user) {
        return jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, user.getId());
    }

    @Test
    void catalogueCarriesNamesCountsAndUses() throws Exception {
        User hidden = user(Role.CEO);
        hidden.hide();
        users.save(hidden);
        long activeCeos = jdbc.queryForObject("SELECT count(*) FROM users WHERE role = 'CEO' AND status = 'ACTIVE'", Long.class);

        mvc.perform(get("/api/admin/roles").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles.length()").value(Role.values().length))
                .andExpect(jsonPath("$.data.roles[?(@.role == 'TECHNICAL_INTERVIEWER')].name").value("Technical interviewer"))
                .andExpect(jsonPath("$.data.roles[?(@.role == 'CEO')].memberCount").value((int) activeCeos))
                .andExpect(jsonPath("$.data.roles[?(@.role == 'HR_CHIEF')].uses[0]").value("Gives the final offer approval"))
                .andExpect(jsonPath("$.data.roles[?(@.role == 'ADMIN')].editable").value(false))
                .andExpect(jsonPath("$.data.roles[?(@.role == 'ADMIN')].permissions.length()").value(10));
    }

    @Test
    void renameIsAudited() throws Exception {
        rename(Role.HR_CHIEF, "Head of people", "Leads HR", version(Role.HR_CHIEF)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Head of people"));

        assertThat(jdbc.queryForObject("SELECT name FROM roles WHERE code = 'HR_CHIEF'", String.class)).isEqualTo("Head of people");
        assertThat(jdbc.queryForObject("SELECT updated_by FROM roles WHERE code = 'HR_CHIEF'", String.class)).isEqualTo(admin.getId());
        assertThat(jdbc.queryForObject("SELECT details FROM audit_events WHERE action = 'ROLE_UPDATE' AND target_id = 'HR_CHIEF' ORDER BY occurred_at DESC LIMIT 1",
                String.class)).startsWith("name \"HR chief\" -> \"Head of people\"");
    }

    @Test
    void staleRenameIs409() throws Exception {
        long seen = version(Role.CEO);
        rename(Role.CEO, "Chief executive", "x", seen).andExpect(status().isOk());

        rename(Role.CEO, "Managing director", "y", seen).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(RoleService.STALE));
        assertThat(jdbc.queryForObject("SELECT name FROM roles WHERE code = 'CEO'", String.class)).isEqualTo("Chief executive");
    }

    @Test
    void duplicateNameIs409() throws Exception {
        rename(Role.CEO, "recruiter", "x", version(Role.CEO)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Another role already uses this name"));
    }

    @Test
    void stalePermissionUpdateIs409() throws Exception {
        long seen = version(Role.HR_CHIEF);
        putPermissions("HR_CHIEF", "[\"SESSION_RUN\"]", seen).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions.length()").value(1));
        assertThat(jdbc.queryForObject("SELECT details FROM audit_events WHERE action = 'ROLE_PERMISSIONS_CHANGE' ORDER BY occurred_at DESC LIMIT 1", String.class))
                .isEqualTo("HR_CHIEF +SESSION_RUN -SESSION_VIEW_ALL -CANDIDATE_REVIEW -OFFER_APPROVE -ANALYTICS_VIEW");

        putPermissions("HR_CHIEF", "[\"ANALYTICS_VIEW\"]", seen).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(RoleService.STALE));
        assertThat(jdbc.queryForList("SELECT permission FROM role_permissions WHERE role = 'HR_CHIEF'", String.class)).containsExactly("SESSION_RUN");
    }

    @Test
    void adminPermissionsStayLocked() throws Exception {
        putPermissions("ADMIN", "[]", version(Role.ADMIN)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The admin role always has every permission"));
        putPermissions("HR_CHIEF", "[\"ANALYTICS_VIEW\",\"ROLE_MANAGE\"]", version(Role.HR_CHIEF)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only admins can manage roles, users or the audit log"));
        putPermissions("HR_CHIEF", "[\"SESSION_RUNN\"]", version(Role.HR_CHIEF)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details").value(containsString("SESSION_RUNN")));
        putPermissions("MANAGER", "[]", 0).andExpect(status().isBadRequest());
    }

    @Test
    void candidateRoleHasNoPermissionEditor() throws Exception {
        mvc.perform(get("/api/admin/roles/CANDIDATE").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.data.editable").value(false))
                .andExpect(jsonPath("$.data.kind").value("CANDIDATE"))
                .andExpect(jsonPath("$.data.permissions.length()").value(0));
        putPermissions("CANDIDATE", "[\"SESSION_RUN\"]", version(Role.CANDIDATE)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Candidates can't hold permissions"));
    }

    @Test
    void membersArePagedAndSearchable() throws Exception {
        String tag = java.util.UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 3; i++) {
            User u = user(Role.CHAIRMAN);
            u.setFullName("Board " + tag + " " + i);
            users.save(u);
        }
        mvc.perform(get("/api/admin/roles/CHAIRMAN/members").param("q", tag).param("size", "2").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].fullName").value("Board " + tag + " 0"));
        mvc.perform(get("/api/admin/roles/CHAIRMAN/members").param("size", "500").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.data.size").value(RoleController.MAX_PAGE));
    }

    @Test
    void bulkMoveMovesEveryoneAndShowsInHistory() throws Exception {
        User a = user(Role.RECRUITER);
        User b = user(Role.RECRUITER);
        move(Role.TECHNICAL_INTERVIEWER, admin, a.getId(), b.getId()).andExpect(status().isOk());

        assertThat(roleOf(a)).isEqualTo("TECHNICAL_INTERVIEWER");
        assertThat(roleOf(b)).isEqualTo("TECHNICAL_INTERVIEWER");
        mvc.perform(get("/api/admin/roles/TECHNICAL_INTERVIEWER/history").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.data.content[0].action").value("ROLE_MEMBERS_MOVED"))
                .andExpect(jsonPath("$.data.content[0].details").value(containsString(a.getEmail())))
                .andExpect(jsonPath("$.data.content[0].actorEmail").value(admin.getEmail()));
    }

    @Test
    void bulkMoveIsAllOrNothing() throws Exception {
        User a = user(Role.RECRUITER);
        User b = user(Role.RECRUITER);
        move(Role.HR_INTERVIEWER, admin, a.getId(), b.getId(), "no-such-user").andExpect(status().isNotFound());
        assertThat(roleOf(a)).isEqualTo("RECRUITER");
        assertThat(roleOf(b)).isEqualTo("RECRUITER");
    }

    @Test
    void bulkMoveCannotIncludeYourself() throws Exception {
        User a = user(Role.RECRUITER);
        move(Role.RECRUITER, admin, a.getId(), admin.getId()).andExpect(status().isBadRequest());
        assertThat(roleOf(admin)).isEqualTo("ADMIN");
        assertThat(roleOf(a)).isEqualTo("RECRUITER");
    }

    @Test
    void bulkMoveCannotMixStaffAndCandidates() throws Exception {
        User a = user(Role.RECRUITER);
        User nour = user(Role.CANDIDATE);
        move(Role.HR_INTERVIEWER, admin, a.getId(), nour.getId()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Candidate and staff accounts can't switch roles"));
        assertThat(roleOf(a)).isEqualTo("RECRUITER");
        assertThat(roleOf(nour)).isEqualTo("CANDIDATE");
    }

    @Test
    void historyListsOnlyThatRole() throws Exception {
        rename(Role.CHAIRMAN, "Board chair", "x", version(Role.CHAIRMAN)).andExpect(status().isOk());
        String body = mvc.perform(get("/api/admin/roles/CEO/history").param("size", "100").header("Authorization", bearer(admin)))
                .andReturn().getResponse().getContentAsString();
        List<String> details = JsonPath.read(body, "$.data.content[*].details");
        assertThat(details).noneMatch(d -> d != null && d.contains("Board chair"));
        mvc.perform(get("/api/admin/roles/CHAIRMAN/history").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.data.content[0].details").value(containsString("Board chair")));
    }

    @Test
    void membersNeedUserManageToo() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(admin.getEmail(), null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGE"))));
        assertThatThrownBy(() -> controller.members(Role.RECRUITER, null, 0, 20)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.history(Role.RECRUITER, 0, 20)).isInstanceOf(AccessDeniedException.class);
    }
}
