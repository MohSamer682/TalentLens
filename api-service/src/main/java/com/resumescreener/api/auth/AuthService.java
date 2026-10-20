package com.resumescreener.api.auth;

import com.resumescreener.api.auth.dto.*;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import com.resumescreener.api.common.ApiErrorCode;
import com.resumescreener.api.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsServiceImpl userDetailsService;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new EmailTakenException("Email already registered");
        }

        // Public sign-up is for candidates; staff accounts come from an admin, and the first admin from BootstrapAdmin
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(Role.CANDIDATE)
                .build();

        user = userRepository.save(user);
        String token = jwtService.generateToken(user.getEmail(), user.getRole());

        return new AuthResponse(token, mapToUserDto(user));
    }

    /** Password first, door second, so only someone who knows the password learns which door the account uses. */
    public AuthResponse login(LoginRequest request, Portal portal) {
        try {
            authenticationManager.authenticate(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            request.getEmail(), request.getPassword()
                    )
            );
        } catch (DisabledException e) {
            // Only someone who knows the password learns the account exists and is hidden
            boolean passwordMatches = userRepository.findByEmailIgnoreCase(request.getEmail())
                    .map(u -> passwordEncoder.matches(request.getPassword(), u.getPasswordHash())).orElse(false);
            if (passwordMatches) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, ApiErrorCode.AUTHENTICATION_FAILED,
                        "This account is disabled. Contact your administrator.", "The account is hidden");
            }
            throw new BadCredentialsException("Invalid email or password");
        } catch (Exception e) {
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (Portal.of(user.getRole()) != portal) {
            throw new ApiException(HttpStatus.FORBIDDEN, ApiErrorCode.WRONG_PORTAL, portal.wrongDoor(),
                    user.getRole().kind() + " account at the " + portal.audience() + " sign-in");
        }

        String token = jwtService.generateToken(user.getEmail(), user.getRole());
        return new AuthResponse(token, mapToUserDto(user));
    }

    /** Issues a session for a user whose identity was proven another way, such as a redeemed access link. */
    public AuthResponse signIn(User user) {
        return new AuthResponse(jwtService.generateToken(user.getEmail(), user.getRole()), mapToUserDto(user));
    }

    /** The signed-in user as they are now, so the UI picks up a role or permission change without signing out. */
    public AuthResponse.UserDto me(String email) {
        return userRepository.findByEmailIgnoreCase(email).map(this::mapToUserDto)
                .orElseThrow(() -> new BadCredentialsException("User no longer exists"));
    }

    private AuthResponse.UserDto mapToUserDto(User user) {
        AuthResponse.UserDto dto = new AuthResponse.UserDto();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setFullName(user.getFullName());
        dto.setRole(user.getRole().name());
        dto.setMustChangePassword(user.isMustChangePassword());
        dto.setPermissions(userDetailsService.permissionsOf(user).stream().map(Enum::name).sorted().toList());
        return dto;
    }
}
