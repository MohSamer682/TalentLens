package com.resumescreener.api.profile;

import com.resumescreener.api.support.ApiIntegrationTest;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileApiTest extends ApiIntegrationTest {

    @Autowired UserProfileRepository profiles;

    private String body(String fullName, String phone, long version) {
        return "{\"fullName\":\"" + fullName + "\",\"phone\":" + (phone == null ? "null" : "\"" + phone + "\"")
                + ",\"headline\":\"Senior Recruiter\",\"department\":\"People\",\"location\":\"Cairo\",\"bio\":\"Hi\",\"version\":" + version + "}";
    }

    @Test
    void profileOfAUserWithNoProfileRowHasBlankFields() throws Exception {
        User user = user(Role.RECRUITER);
        mvc.perform(get("/api/me/profile").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(user.getEmail()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.headline").doesNotExist())
                .andExpect(jsonPath("$.data.version").value(0));
    }

    @Test
    void updateProfileCreatesRowOnFirstSave() throws Exception {
        User user = user(Role.RECRUITER);
        mvc.perform(put("/api/me/profile").header("Authorization", bearer(user)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Karim Adel", "+20 100 123 4567", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Karim Adel"))
                .andExpect(jsonPath("$.data.version").value(1));

        assertThat(profiles.findById(user.getId()).orElseThrow().getHeadline()).isEqualTo("Senior Recruiter");
        assertThat(users.findById(user.getId()).orElseThrow().getFullName()).isEqualTo("Karim Adel");
    }

    @Test
    void staleVersionReturns409Envelope() throws Exception {
        User user = user(Role.RECRUITER);
        String token = bearer(user);
        mvc.perform(put("/api/me/profile").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(body("First", null, 0))).andExpect(status().isOk());

        mvc.perform(put("/api/me/profile").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Second", null, 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
        assertThat(users.findById(user.getId()).orElseThrow().getFullName()).isEqualTo("First");
    }

    @Test
    void blankPhoneMeansNoPhone() throws Exception {
        User user = user(Role.RECRUITER);
        mvc.perform(put("/api/me/profile").header("Authorization", bearer(user)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Karim", "", 0)))
                .andExpect(status().isOk());
        assertThat(profiles.findById(user.getId()).orElseThrow().getPhone()).isNull();
    }

    @Test
    void invalidPhoneIs400() throws Exception {
        User user = user(Role.RECRUITER);
        mvc.perform(put("/api/me/profile").header("Authorization", bearer(user)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Karim", "call me", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details").value(org.hamcrest.Matchers.startsWith("phone")));
    }

    private String password(String current, String next) {
        return "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}";
    }

    @Test
    void wrongCurrentPasswordReturns400() throws Exception {
        User user = user(Role.RECRUITER);
        mvc.perform(put("/api/me/password").header("Authorization", bearer(user)).contentType(MediaType.APPLICATION_JSON)
                        .content(password("not-my-password", "AnotherPass123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Your current password is not right"));
    }

    @Test
    void newPasswordMustDifferFromCurrent() throws Exception {
        User user = user(Role.RECRUITER);
        mvc.perform(put("/api/me/password").header("Authorization", bearer(user)).contentType(MediaType.APPLICATION_JSON)
                        .content(password(PASSWORD, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePasswordClearsFlagAndUnlocksTheApi() throws Exception {
        User user = user(Role.RECRUITER);
        user.resetPassword(encoder.encode(PASSWORD));
        users.save(user);
        String token = bearer(user);

        mvc.perform(put("/api/me/password").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(password(PASSWORD, "MyOwnPassword42")))
                .andExpect(status().isOk());

        User saved = users.findById(user.getId()).orElseThrow();
        assertThat(saved.isMustChangePassword()).isFalse();
        assertThat(encoder.matches("MyOwnPassword42", saved.getPasswordHash())).isTrue();
        mvc.perform(get("/api/sessions").header("Authorization", token)).andExpect(status().isOk());
    }
}
