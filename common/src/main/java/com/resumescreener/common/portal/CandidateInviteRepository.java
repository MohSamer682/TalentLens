package com.resumescreener.common.portal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CandidateInviteRepository extends JpaRepository<CandidateInvite, String> {

    /** Row-locked, so two redemptions of one link run one after the other and the second sees it used. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from CandidateInvite i where i.tokenHash = :hash")
    Optional<CandidateInvite> lockByTokenHash(String hash);

    Optional<CandidateInvite> findByTokenHash(String tokenHash);

    /** The candidate's live link, if any; the partial unique index allows at most one. */
    Optional<CandidateInvite> findByCandidateIdAndUsedAtIsNullAndRevokedAtIsNull(String candidateId);
}
