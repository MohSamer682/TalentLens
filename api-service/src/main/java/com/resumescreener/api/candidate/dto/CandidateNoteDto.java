package com.resumescreener.api.candidate.dto;

import com.resumescreener.common.session.CandidateNote;
import java.time.Instant;

public record CandidateNoteDto(
    String id,
    String candidateId,
    String authorId,
    String authorName,
    String authorRole,
    String content,
    Instant createdAt,
    boolean deletable
) {
    public static CandidateNoteDto from(CandidateNote note, String currentUserId, boolean isAdmin) {
        boolean canDelete = isAdmin || (note.getAuthor() != null && note.getAuthor().getId().equals(currentUserId));
        return new CandidateNoteDto(
            note.getId(),
            note.getCandidateId(),
            note.getAuthor() != null ? note.getAuthor().getId() : null,
            note.getAuthor() != null ? note.getAuthor().getFullName() : "Unknown",
            note.getAuthor() != null && note.getAuthor().getRole() != null ? note.getAuthor().getRole().name() : null,
            note.getContent(),
            note.getCreatedAt(),
            canDelete
        );
    }
}
