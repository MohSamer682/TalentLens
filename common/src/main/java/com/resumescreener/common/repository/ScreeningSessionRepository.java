package com.resumescreener.common.repository;

import com.resumescreener.common.entity.ScreeningSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ScreeningSessionRepository extends JpaRepository<ScreeningSession, String> {
    Page<ScreeningSession> findByUserId(String userId, Pageable pageable);
    Optional<ScreeningSession> findByIdAndUserId(String id, String userId);

    @EntityGraph(attributePaths = {"weights"})
    Optional<ScreeningSession> findWithWeightsById(String id);
}