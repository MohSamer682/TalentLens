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

import com.resumescreener.common.session.Candidate;
import com.resumescreener.common.user.User;

import java.time.Clock;
import java.time.Duration;
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

    /** The raw token leaves the server once, in the link; the invite keeps only its hash. */
    public record Issued(CandidateInvite invite, String rawToken) {
    }

    public static Issued issue(Candidate candidate, User creator, Clock clock, Duration ttl) {
        String raw = InviteToken.generate();
        Instant now = clock.instant();
        CandidateInvite invite = CandidateInvite.builder()
                .candidateId(candidate.getId())
                .tokenHash(InviteToken.hash(raw))
                .createdBy(creator.getId())
                .createdAt(now)
                .expiresAt(now.plus(ttl))
                .build();
        return new Issued(invite, raw);
    }

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
