package com.resumescreener.api.profile;

import com.resumescreener.api.common.ApiResponse;
import com.resumescreener.api.profile.dto.ChangePasswordRequest;
import com.resumescreener.api.profile.dto.ProfileView;
import com.resumescreener.api.profile.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<ProfileView>> profile(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Profile loaded", profileService.view(auth.getName())));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<ProfileView>> update(@Valid @RequestBody UpdateProfileRequest request, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Profile saved", profileService.updateOwn(auth.getName(), request)));
    }

    @PutMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication auth) {
        profileService.changePassword(auth.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("Password changed", null));
    }
}
