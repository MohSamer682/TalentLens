package com.resumescreener.api.portal.dto;

import java.time.Instant;

/** The only response that ever carries the raw link. */
public record IssuedInvite(String url, Instant expiresAt) {
}
