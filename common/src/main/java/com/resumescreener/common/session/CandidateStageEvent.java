package com.resumescreener.common.session;

import com.resumescreener.common.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

@Entity
@Table(name = "candidate_stage_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateStageEvent {

    @Id
    @UuidGenerator
    private String id;

    @Column(name = "candidate_id", nullable = false)
    private String candidateId;

    @Column(nullable = false, length = 32)
    private String stage;

    @Column(name = "actor_id")
    private String actorId;

    @Column(name = "actor_name")
    private String actorName;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
