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

    /** Atomic compare-and-set so two concurrent starts cannot both win; clears the previous run's error. */
    @Modifying
    @Query("update ScreeningSession s set s.status = com.resumescreener.common.session.SessionStatus.RUNNING, "
            + "s.lastErrorMessage = null "
            + "where s.id = :id and s.status <> com.resumescreener.common.session.SessionStatus.RUNNING")
    int markRunning(@Param("id") String id);

    /** Single DELETE: the database cascades to candidates, scores, weights and jobs (V13). */
    @Modifying
    @Query("delete from ScreeningSession s where s.id = :id")
    int deleteByIdDirect(@Param("id") String id);

    List<ScreeningSession> findByStatus(SessionStatus status);

    @EntityGraph(attributePaths = {"weights", "requiredSkills"})
    Optional<ScreeningSession> findWithWeightsById(String id);

    @EntityGraph(attributePaths = {"user"})
    Optional<ScreeningSession> findWithUserById(String id);

    @Query("SELECT s FROM ScreeningSession s WHERE LOWER(s.user.email) = LOWER(:email) ORDER BY s.createdAt DESC")
    List<ScreeningSession> findByUserEmailOrderByCreatedAtDesc(@Param("email") String email);

    List<ScreeningSession> findByUser_EmailOrderByCreatedAtDesc(String email);
}