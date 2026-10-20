package com.resumescreener.common.user;

import java.time.Instant;

/** One line of the admin user list: the user plus their job title, in a single query. */
public record UserRow(String id, String email, String fullName, Role role, UserStatus status, String headline, Instant createdAt) {
}
