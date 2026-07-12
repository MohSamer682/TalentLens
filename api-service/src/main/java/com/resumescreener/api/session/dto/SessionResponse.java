package com.resumescreener.api.session.dto;

import com.resumescreener.common.enums.SessionStatus;
import com.resumescreener.common.dto.WeightsDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.Instant;

@Data
@AllArgsConstructor
public class SessionResponse {
    private String id;
    private String title;
    private String description;
    private SessionStatus status;
    private String jdFileKey;
    private Instant createdAt;
    private Instant completedAt;
    private WeightsDto weights;
    private Integer candidateCount;
    private String lastErrorMessage;
    private String reportFileKey;
}
