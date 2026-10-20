package com.resumescreener.common.portal;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** A one-time link that lets a shortlisted candidate set a password. Only the SHA-256 of the token is kept. */
@Entity
@Table(name = "candidate_invites")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CandidateInvite {

    @Id
    @UuidGenerator
    private String id;

    @Column(nullable = false)
    private String candidateId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant usedAt;
    private Instant revokedAt;

    @Column(nullable = false)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    public boolean isOpen() {
        return usedAt == null && revokedAt == null;
    }

    public boolean isUsable(Instant now) {
        return isOpen() && now.isBefore(expiresAt);
    }

    public void revoke(Instant at) {
        if (isOpen()) revokedAt = at;
    }

    public void markUsed(Instant at) {
        usedAt = at;
    }
}
