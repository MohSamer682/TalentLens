package com.resumescreener.api.candidate.dto;

import com.resumescreener.common.session.CandidateOffer;

import java.time.Instant;

public record CandidateOfferDto(
    String id,
    String candidateId,
    String fileName,
    long sizeBytes,
    String uploadedById,
    String uploadedByName,
    Instant uploadedAt,
    String approvedById,
    String approvedByName,
    Instant approvedAt,
    boolean isApproved
) {
    public static CandidateOfferDto from(CandidateOffer offer) {
        return new CandidateOfferDto(
            offer.getId(),
            offer.getCandidateId(),
            offer.getFileName(),
            offer.getSizeBytes(),
            offer.getUploadedBy() != null ? offer.getUploadedBy().getId() : null,
            offer.getUploadedBy() != null ? offer.getUploadedBy().getFullName() : "Unknown",
            offer.getUploadedAt(),
            offer.getApprovedBy() != null ? offer.getApprovedBy().getId() : null,
            offer.getApprovedBy() != null ? offer.getApprovedBy().getFullName() : null,
            offer.getApprovedAt(),
            offer.getApprovedAt() != null
        );
    }
}
