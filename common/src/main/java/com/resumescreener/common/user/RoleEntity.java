package com.resumescreener.common.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** The editable side of a role: its label and description. What the role means to the workflow stays in {@link Role}. */
@Entity
@Table(name = "roles")
@Getter
@NoArgsConstructor
public class RoleEntity {

    @Id
    @Enumerated(EnumType.STRING)
    private Role code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 300)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role.Kind kind;

    @Column(nullable = false)
    private boolean system = true;

    private Instant updatedAt;

    private String updatedBy;

    @Version
    private long version;

    public void rename(String name, String description, String byUserId, Instant at) {
        this.name = name;
        this.description = description;
        touch(byUserId, at);
    }

    /** Marks a change that lives in another table, such as the role's permissions, so the version still moves. */
    public void touch(String byUserId, Instant at) {
        this.updatedBy = byUserId;
        this.updatedAt = at;
    }
}
