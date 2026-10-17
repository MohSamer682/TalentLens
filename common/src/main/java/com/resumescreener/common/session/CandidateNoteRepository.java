package com.resumescreener.common.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CandidateNoteRepository extends JpaRepository<CandidateNote, String> {

    @Query("SELECT n FROM CandidateNote n JOIN FETCH n.author WHERE n.candidateId = :candidateId ORDER BY n.createdAt DESC")
    List<CandidateNote> findByCandidateIdOrderByCreatedAtDesc(@Param("candidateId") String candidateId);
}
