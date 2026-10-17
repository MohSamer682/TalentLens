package com.resumescreener.api.candidate.dto;

import com.resumescreener.common.session.CandidateOffer;

import java.time.Instant;
import java.time.LocalDate;

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
    boolean isApproved,
    boolean requiresDirectorApproval,
    String directorApprovedByName,
    Instant directorApprovedAt,
    String rejectedByName,
    Instant rejectedAt,
    String rejectionNote,
    Instant sentAt,
    String candidateResponse,
    Instant respondedAt,
    LocalDate joiningDate,
    String joiningNotes
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
            offer.getApprovedAt() != null,
            offer.isRequiresDirectorApproval(),
            offer.getDirectorApprovedBy() != null ? offer.getDirectorApprovedBy().getFullName() : null,
            offer.getDirectorApprovedAt(),
            offer.getRejectedBy() != null ? offer.getRejectedBy().getFullName() : null,
            offer.getRejectedAt(),
            offer.getRejectionNote(),
            offer.getSentAt(),
            offer.getCandidateResponse(),
            offer.getRespondedAt(),
            offer.getJoiningDate(),
            offer.getJoiningNotes()
        );
    }
}
