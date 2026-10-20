package com.resumescreener.api.portal;

import com.resumescreener.common.portal.CandidateInvite;
import com.resumescreener.common.portal.InviteToken;
import com.resumescreener.common.session.Candidate;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.User;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InviteTokenTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-20T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void rawTokenIsNotStoredAndHashMatches() {
        Candidate candidate = Candidate.builder().id("c1").build();
        User hr = User.builder().id("u1").role(Role.RECRUITER).build();

        CandidateInvite.Issued issued = CandidateInvite.issue(candidate, hr, clock, Duration.ofDays(7));

        assertThat(issued.rawToken()).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(issued.invite().getTokenHash()).isEqualTo(InviteToken.hash(issued.rawToken())).hasSize(64)
                .doesNotContain(issued.rawToken());
        assertThat(issued.invite().getCandidateId()).isEqualTo("c1");
        assertThat(issued.invite().getCreatedBy()).isEqualTo("u1");
        assertThat(issued.invite().getExpiresAt()).isEqualTo(Instant.parse("2026-10-27T10:00:00Z"));
    }

    @Test
    void tokensAreUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            seen.add(InviteToken.generate());
        }
        assertThat(seen).hasSize(1000);
    }

    @Test
    void anInviteIsUsableOnlyBeforeItExpiresAndWhileOpen() {
        CandidateInvite invite = CandidateInvite.issue(Candidate.builder().id("c1").build(), User.builder().id("u1").build(),
                clock, Duration.ofDays(7)).invite();
        Instant now = clock.instant();

        assertThat(invite.isUsable(now)).isTrue();
        assertThat(invite.isUsable(now.plus(Duration.ofDays(7)))).isFalse();
        invite.revoke(now);
        assertThat(invite.isUsable(now)).isFalse();
    }
}
