package com.resumescreener.common.portal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CandidateInviteRepository extends JpaRepository<CandidateInvite, String> {

    Optional<CandidateInvite> findByTokenHash(String tokenHash);

    /** The candidate's live link, if any; the partial unique index allows at most one. */
    Optional<CandidateInvite> findByCandidateIdAndUsedAtIsNullAndRevokedAtIsNull(String candidateId);
}
