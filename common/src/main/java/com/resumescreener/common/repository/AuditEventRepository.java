package com.resumescreener.common.repository;

import com.resumescreener.common.entity.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuditEventRepository extends JpaRepository<AuditEvent, String> {

    Page<AuditEvent> findByActorUserId(String actorUserId, Pageable pageable);

    Page<AuditEvent> findByAction(String action, Pageable pageable);

    @Query("SELECT a FROM AuditEvent a WHERE " +
           "(:userId IS NULL OR a.actorUserId = :userId) AND " +
           "(:action IS NULL OR a.action = :action) " +
           "ORDER BY a.occurredAt DESC")
    Page<AuditEvent> findFiltered(String userId, String action, Pageable pageable);
}
