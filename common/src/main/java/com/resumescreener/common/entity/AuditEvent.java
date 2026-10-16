package com.resumescreener.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

@Entity
@Table(name = "audit_event")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditEvent {

    @Id
    @UuidGenerator
    private String id;

    @Column(nullable = false)
    private Instant occurredAt;

    private String actorUserId;

    @Column(nullable = false)
    private String action;

    private String targetType;
    private String targetId;
    private String requestId;
    private String ip;
    private String outcome;
    private String details;
}
