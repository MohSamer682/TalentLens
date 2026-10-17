package com.resumescreener.api.auth;

import com.resumescreener.api.auth.dto.*;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
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

        long userCount = userRepository.count();
        Role role = userCount == 0 ? Role.ADMIN : Role.RECRUITER;

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(role)
                .build();

        user = userRepository.save(user);
        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());

        return new AuthResponse(token, mapToUserDto(user));
    }

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            request.getEmail(), request.getPassword()
                    )
            );
        } catch (Exception e) {
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        return new AuthResponse(token, mapToUserDto(user));
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
        dto.setPermissions(userDetailsService.permissionsOf(user).stream().map(Enum::name).sorted().toList());
        return dto;
    }
}
