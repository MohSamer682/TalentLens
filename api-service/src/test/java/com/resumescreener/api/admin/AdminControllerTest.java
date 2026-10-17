package com.resumescreener.api.admin;

import com.resumescreener.api.audit.AuditService;
import com.resumescreener.api.config.GlobalExceptionHandler;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminControllerTest {

    @Test
    void canAssignHrChief() throws Exception {
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(User.builder().id("admin").email("admin@example.com").role(Role.ADMIN).build()));
        when(users.findById("u2")).thenReturn(Optional.of(User.builder().id("u2").role(Role.RECRUITER).build()));
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AdminController(users, mock(AuditService.class), null))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(patch("/api/admin/users/u2/role")
                        .principal(new TestingAuthenticationToken("admin@example.com", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"HR_CHIEF\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("HR_CHIEF"));
    }

    @Test
    void cannotDemoteLastAdmin() throws Exception {
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmailIgnoreCase("hr@example.com"))
                .thenReturn(Optional.of(User.builder().id("hr").email("hr@example.com").role(Role.HR_CHIEF).build()));
        when(users.findById("admin")).thenReturn(Optional.of(User.builder().id("admin").role(Role.ADMIN).build()));
        when(users.countByRole(Role.ADMIN)).thenReturn(1L);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AdminController(users, mock(AuditService.class), null))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(patch("/api/admin/users/admin/role")
                        .principal(new TestingAuthenticationToken("hr@example.com", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"RECRUITER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("At least one admin must remain"));
        verify(users, never()).save(any(User.class));
    }
}
