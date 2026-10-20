package com.resumescreener.api.profile.dto;

import com.resumescreener.common.user.ProfileFields;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 120) String fullName,
        @Pattern(regexp = "^\\s*$|^[+0-9 ()-]{6,32}$", message = "can use digits, spaces, +, - and brackets") String phone,
        @Size(max = 120) String headline,
        @Size(max = 80) String department,
        @Size(max = 120) String location,
        @Size(max = 1000) String bio,
        @NotNull Long version) {

    public ProfileFields fields() {
        return new ProfileFields(blankToNull(phone), blankToNull(headline), blankToNull(department),
                blankToNull(location), blankToNull(bio));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
