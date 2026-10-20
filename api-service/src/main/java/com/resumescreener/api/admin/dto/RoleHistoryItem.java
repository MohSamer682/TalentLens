package com.resumescreener.api.admin.dto;

import com.resumescreener.common.audit.AuditEvent;

import java.time.Instant;

public record RoleHistoryItem(String id, String action, String actorEmail, String details, Instant occurredAt) {
    public static RoleHistoryItem of(AuditEvent event) {
        return new RoleHistoryItem(event.getId(), event.getAction(), event.getActorEmail(), event.getDetails(), event.getOccurredAt());
    }
}
