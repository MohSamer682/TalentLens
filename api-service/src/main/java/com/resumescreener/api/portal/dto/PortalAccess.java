package com.resumescreener.api.portal.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/** HR's view of a candidate's portal access: the linked account and the open link, either may be missing. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PortalAccess(Account account, OpenInvite invite) {

    public record Account(String email, Instant createdAt, boolean mustChangePassword, boolean active) {
    }

    public record OpenInvite(Instant createdAt, Instant expiresAt) {
    }
}
