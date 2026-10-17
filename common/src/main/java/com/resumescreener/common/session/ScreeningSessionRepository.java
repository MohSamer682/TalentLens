package com.resumescreener.common.session;

import com.resumescreener.common.session.ScreeningSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ScreeningSessionRepository extends JpaRepository<ScreeningSession, String> {
    Page<ScreeningSession> findByUserId(String userId, Pageable pageable);
    Optional<ScreeningSession> findByIdAndUserId(String id, String userId);

    /** Atomic compare-and-set so two concurrent starts cannot both win. */
    @Modifying
    @Query("update ScreeningSession s set s.status = com.resumescreener.common.session.SessionStatus.RUNNING "
            + "where s.id = :id and s.status <> com.resumescreener.common.session.SessionStatus.RUNNING")
    int markRunning(@Param("id") String id);

    List<ScreeningSession> findByStatus(SessionStatus status);

    @EntityGraph(attributePaths = {"weights"})
    Optional<ScreeningSession> findWithWeightsById(String id);
}