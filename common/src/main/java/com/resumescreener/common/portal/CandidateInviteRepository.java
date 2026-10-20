package com.resumescreener.common.portal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CandidateInviteRepository extends JpaRepository<CandidateInvite, String> {

    /**
     * A scalar, so nothing enters the persistence context before the candidate row is locked; loading the
     * invite here would let a later locked read hand back this unlocked snapshot.
     */
    @Query("select i.candidateId from CandidateInvite i where i.tokenHash = :hash")
    Optional<String> findCandidateIdByTokenHash(String hash);

    /** Take the candidate's row lock first: every portal change locks candidate, then invite, so they can't deadlock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from CandidateInvite i where i.tokenHash = :hash")
    Optional<CandidateInvite> lockByTokenHash(String hash);

    Optional<CandidateInvite> findByTokenHash(String tokenHash);

    /** The candidate's live link, if any; the partial unique index allows at most one. */
    Optional<CandidateInvite> findByCandidateIdAndUsedAtIsNullAndRevokedAtIsNull(String candidateId);
}
