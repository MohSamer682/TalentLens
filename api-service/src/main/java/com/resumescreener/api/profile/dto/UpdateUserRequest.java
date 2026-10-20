package com.resumescreener.api.profile.dto;

import com.resumescreener.common.user.ProfileFields;
import com.resumescreener.common.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 120) String fullName,
        @NotNull Role role,
        @Pattern(regexp = "^[+0-9 ()-]{6,32}$", message = "can use digits, spaces, +, - and brackets") String phone,
        @Size(max = 120) String headline,
        @Size(max = 80) String department,
        @Size(max = 120) String location,
        @Size(max = 1000) String bio,
        @NotNull Long version) {

    public ProfileFields fields() {
        return new UpdateProfileRequest(fullName, phone, headline, department, location, bio, version).fields();
    }
}
