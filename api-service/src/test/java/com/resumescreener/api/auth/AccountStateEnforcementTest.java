package com.resumescreener.api.auth;

import com.resumescreener.api.support.ApiIntegrationTest;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountStateEnforcementTest extends ApiIntegrationTest {

    private String login(User user, String password) {
        return "{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void hiddenUserTokenStopsWorking() throws Exception {
        User user = user(Role.RECRUITER);
        String token = bearer(user);
        mvc.perform(get("/api/sessions").header("Authorization", token)).andExpect(status().isOk());

        User loaded = users.findById(user.getId()).orElseThrow();
        loaded.hide();
        users.save(loaded);

        mvc.perform(get("/api/sessions").header("Authorization", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void deletedUserTokenIs401NotAServerError() throws Exception {
        User user = user(Role.RECRUITER);
        String token = bearer(user);
        users.deleteById(user.getId());

        mvc.perform(get("/api/sessions").header("Authorization", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void hiddenUserWithTheRightPasswordIsToldTheAccountIsDisabled() throws Exception {
        User user = user(Role.RECRUITER);
        user.hide();
        users.save(user);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(user, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("This account is disabled. Contact your administrator."));
    }

    @Test
    void hiddenUserWithAWrongPasswordGetsThePlainMessage() throws Exception {
        User user = user(Role.RECRUITER);
        user.hide();
        users.save(user);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(user, "nope-nope-nope")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void loginTellsTheUiAPasswordChangeIsDue() throws Exception {
        User user = user(Role.RECRUITER);
        user.resetPassword(encoder.encode(PASSWORD));
        users.save(user);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(user, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.mustChangePassword").value(true));
    }

    @Test
    void mustChangePasswordBlocksOtherEndpointsWith403Envelope() throws Exception {
        User user = user(Role.RECRUITER);
        user.resetPassword(encoder.encode(PASSWORD));
        users.save(user);

        mvc.perform(get("/api/sessions").header("Authorization", bearer(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));
    }
}
