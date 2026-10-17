package com.resumescreener.api.admin;

import com.resumescreener.api.audit.AuditService;
import com.resumescreener.api.config.GlobalExceptionHandler;
import com.resumescreener.common.user.Permission;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.RolePermission;
import com.resumescreener.common.user.RolePermissionRepository;
import com.resumescreener.common.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoleControllerTest {

    private final RolePermissionRepository grants = mock(RolePermissionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(grants.findByRole(Role.HR_CHIEF)).thenReturn(List.of(
                new RolePermission(Role.HR_CHIEF, Permission.SESSION_RUN),
                new RolePermission(Role.HR_CHIEF, Permission.SESSION_VIEW_ALL),
                new RolePermission(Role.HR_CHIEF, Permission.CANDIDATE_REVIEW),
                new RolePermission(Role.HR_CHIEF, Permission.ANALYTICS_VIEW)));
        when(users.countByRole(Role.HR_CHIEF)).thenReturn(2L);
        mvc = MockMvcBuilders.standaloneSetup(new RoleController(new RoleService(grants, users, audit)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listsCatalogAndRoles() throws Exception {
        mvc.perform(get("/api/admin/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions.length()").value(10))
                .andExpect(jsonPath("$.data.roles.length()").value(5))
                .andExpect(jsonPath("$.data.roles[0].role").value("ADMIN"))
                .andExpect(jsonPath("$.data.roles[0].editable").value(false))
                .andExpect(jsonPath("$.data.roles[0].permissions.length()").value(10))
                .andExpect(jsonPath("$.data.roles[1].userCount").value(2));
    }

    @Test
    void replacesGrantsAndAudits() throws Exception {
        putPermissions("HR_CHIEF", "[\"SESSION_RUN\"]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions[0]").value("SESSION_RUN"))
                .andExpect(jsonPath("$.data.permissions.length()").value(1));

        verify(grants).deleteByRole(Role.HR_CHIEF);
        verify(audit).record("ROLE_PERMISSIONS_CHANGE", "role", "HR_CHIEF", "success",
                "HR_CHIEF -SESSION_VIEW_ALL -CANDIDATE_REVIEW -ANALYTICS_VIEW");
    }

    @Test
    void adminRowIsLocked() throws Exception {
        putPermissions("ADMIN", "[]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The admin role always has every permission"));
        verify(grants, never()).deleteByRole(any());
    }

    @Test
    void adminOnlyPermissionsCannotBeGranted() throws Exception {
        putPermissions("HR_CHIEF", "[\"ANALYTICS_VIEW\",\"ROLE_MANAGE\"]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only admins can manage roles, users or the audit log"));
        verify(grants, never()).deleteByRole(any());
        verify(audit, never()).record(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void unknownPermissionIsRejected() throws Exception {
        putPermissions("HR_CHIEF", "[\"SESSION_RUNN\"]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details").value(containsString("SESSION_RUNN")));
        verify(grants, never()).deleteByRole(eq(Role.HR_CHIEF));
    }

    @Test
    void unknownRoleIsRejected() throws Exception {
        putPermissions("MANAGER", "[]").andExpect(status().isBadRequest());
    }

    private ResultActions putPermissions(String role, String permissions) throws Exception {
        return mvc.perform(put("/api/admin/roles/" + role + "/permissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissions\":" + permissions + "}"));
    }
}
