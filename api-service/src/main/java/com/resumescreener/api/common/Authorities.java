package com.resumescreener.api.common;

import com.resumescreener.common.user.Permission;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Permission checks for service code; endpoints use @PreAuthorize("hasAuthority('...')") instead. */
public final class Authorities {

    private Authorities() {
    }

    public static boolean has(Permission permission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> permission.name().equals(a.getAuthority()));
    }
}
