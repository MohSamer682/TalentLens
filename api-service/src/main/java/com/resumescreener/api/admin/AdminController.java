package com.resumescreener.api.admin;

import com.resumescreener.common.entity.User;
import com.resumescreener.common.enums.Role;
import com.resumescreener.common.repository.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;

    public record UserDto(String id, String email, String fullName, Role role, java.time.Instant createdAt) {}

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserDto>> listUsers(Pageable pageable) {
        return ResponseEntity.ok(userRepository.findAll(pageable)
                .map(u -> new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.getCreatedAt())));
    }

    @PatchMapping("/users/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateRole(@PathVariable String id, @RequestBody RoleRequest request, org.springframework.security.core.Authentication auth) {
        User caller = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Caller not found"));
        
        if (caller.getId().equals(id)) {
            return ResponseEntity.badRequest().build();
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        user.setRole(request.getRole());
        userRepository.save(user);
        
        return ResponseEntity.ok().build();
    }

    @Data
    public static class RoleRequest {
        private Role role;
    }
}
