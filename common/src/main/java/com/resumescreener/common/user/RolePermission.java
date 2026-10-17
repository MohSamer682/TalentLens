package com.resumescreener.common.user;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "role_permissions")
@IdClass(RolePermission.Key.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RolePermission {

    @Id
    @Enumerated(EnumType.STRING)
    private Role role;

    @Id
    @Enumerated(EnumType.STRING)
    private Permission permission;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private Role role;
        private Permission permission;
    }
}
