package com.resumescreener.api.auth.dto;

import com.resumescreener.common.user.User;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private UserDto user;

    @Data
    public static class UserDto {
        private String id;
        private String email;
        private String fullName;
        private String role;
    }
}
