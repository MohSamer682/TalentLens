package com.resumescreener.common.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CandidateStageEventRepository extends JpaRepository<CandidateStageEvent, String> {

    List<CandidateStageEvent> findByCandidateIdOrderByCreatedAtDesc(String candidateId);
}
