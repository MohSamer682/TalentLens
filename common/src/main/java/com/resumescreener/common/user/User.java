package com.resumescreener.common.user;

import com.resumescreener.common.user.Role;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true, nullable = false)
    private String email;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(nullable = false)
    @Builder.Default
    private boolean mustChangePassword = false;

    private Instant updatedAt;

    @Version
    private Long version;

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public void hide() {
        status = UserStatus.HIDDEN;
    }

    public void unhide() {
        status = UserStatus.ACTIVE;
    }

    /** An admin-set password is temporary: the user must choose their own on next sign-in. */
    public void resetPassword(String temporaryHash) {
        passwordHash = temporaryHash;
        mustChangePassword = true;
    }

    public void changePassword(String hash) {
        passwordHash = hash;
        mustChangePassword = false;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}