package com.resumescreener.common.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "batch_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ScreeningSession session;

    private String springBatchJobId;
    private Integer total;
    private Integer processed;
    private Integer failed;
    private Instant startedAt;
    private Instant finishedAt;
}