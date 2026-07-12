package com.resumescreener.common.repository;

import com.resumescreener.common.entity.ScoringWeights;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoringWeightsRepository extends JpaRepository<ScoringWeights, String> {
    ScoringWeights findBySessionId(String sessionId);
}