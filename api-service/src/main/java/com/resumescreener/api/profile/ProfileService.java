package com.resumescreener.api.profile;

import com.resumescreener.api.audit.AuditService;
import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.profile.dto.ChangePasswordRequest;
import com.resumescreener.api.profile.dto.ProfileView;
import com.resumescreener.api.profile.dto.UpdateProfileRequest;
import com.resumescreener.common.user.ProfileFields;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserProfile;
import com.resumescreener.common.user.UserProfileRepository;
import com.resumescreener.common.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ProfileView view(String email) {
        User user = byEmail(email);
        return ProfileView.of(user, profileRepository.findById(user.getId()).map(UserProfile::fields).orElse(null));
    }

    /** Shared with admin edits: the version guards the user row, which every profile save touches. */
    @Transactional
    public ProfileView update(User user, String fullName, ProfileFields fields, long expectedVersion, String editorId) {
        requireVersion(user, expectedVersion);
        Instant now = Instant.now(clock);
        user.setFullName(fullName.trim());
        user.setUpdatedAt(now);
        UserProfile profile = profileRepository.findById(user.getId()).orElseGet(() -> UserProfile.blankFor(user));
        profile.update(fields, editorId, now);
        profileRepository.save(profile);
        userRepository.saveAndFlush(user);
        return ProfileView.of(user, profile.fields());
    }

    @Transactional
    public ProfileView updateOwn(String email, UpdateProfileRequest request) {
        User user = byEmail(email);
        ProfileView view = update(user, request.fullName(), request.fields(), request.version(), user.getId());
        auditService.record("PROFILE_UPDATE", "user", user.getId(), "success");
        return view;
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = byEmail(email);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.validation("Your current password is not right", "currentPassword did not match");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw ApiException.validation("Choose a password you have not used here", "newPassword equals currentPassword");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        auditService.record("PASSWORD_CHANGE", "user", user.getId(), "success");
    }

    static void requireVersion(User user, long expected) {
        long current = user.getVersion() == null ? 0 : user.getVersion();
        if (current != expected) {
            throw ApiException.conflict("Someone changed this profile while you were editing. Reload to see their changes.",
                    "Expected version " + expected + " but found " + current);
        }
    }

    private User byEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> ApiException.notFound("User not found", "No user exists for email " + email));
    }
}
