package com.resumescreener.api.profile.dto;

import com.resumescreener.common.user.ProfileFields;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserStatus;

import java.time.Instant;

public record ProfileView(String id, String email, String fullName, Role role, UserStatus status,
                          boolean mustChangePassword, String phone, String headline, String department,
                          String location, String bio, Instant createdAt, long version) {

    public static ProfileView of(User user, ProfileFields profile) {
        ProfileFields p = profile == null ? new ProfileFields(null, null, null, null, null) : profile;
        return new ProfileView(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.getStatus(),
                user.isMustChangePassword(), p.phone(), p.headline(), p.department(), p.location(), p.bio(),
                user.getCreatedAt(), user.getVersion() == null ? 0 : user.getVersion());
    }
}
