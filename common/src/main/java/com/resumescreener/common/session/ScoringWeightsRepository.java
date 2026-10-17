package com.resumescreener.common.session;

import com.resumescreener.common.session.ScoringWeights;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoringWeightsRepository extends JpaRepository<ScoringWeights, String> {
    ScoringWeights findBySessionId(String sessionId);
}