package com.resumescreener.common.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CandidateOfferRepository extends JpaRepository<CandidateOffer, String> {

    @Query("SELECT o FROM CandidateOffer o JOIN FETCH o.uploadedBy LEFT JOIN FETCH o.approvedBy WHERE o.candidateId = :candidateId")
    Optional<CandidateOffer> findByCandidateId(@Param("candidateId") String candidateId);

    @Query("SELECT o FROM CandidateOffer o JOIN FETCH o.uploadedBy LEFT JOIN FETCH o.approvedBy WHERE o.candidateId IN :ids")
    List<CandidateOffer> findByCandidateIdIn(@Param("ids") java.util.Collection<String> ids);

    @Query("SELECT o FROM CandidateOffer o JOIN FETCH o.uploadedBy WHERE o.approvedBy IS NULL ORDER BY o.uploadedAt ASC")
    List<CandidateOffer> findPendingApproval();

    long countByApprovedAtIsNotNull();

    long countByApprovedAtBetween(Instant from, Instant to);
}
